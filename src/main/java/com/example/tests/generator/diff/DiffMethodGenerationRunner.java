package com.example.tests.generator.diff;

import com.example.agent.providers.LLMClient;
import com.example.tests.generator.metadata.MetadataTransformer;
import com.example.tests.generator.model.ClassMetadata;
import com.example.tests.generator.model.MethodMetadata;
import com.example.tests.generator.model.MethodMetadata.Parameter;
import com.example.tests.generator.pipeline.GeneratedTestClass;
import com.example.tests.generator.util.StandardLibraryTypeResolver;
import com.example.tests.generator.validate.ResponseValidator;
import com.example.tests.generator.validate.ValidationResult;
import com.example.tests.generator.verification.GeneratedTestVerifier;
import com.example.tests.orchestration.analysis.DependencyNode;
import com.example.tests.orchestration.analysis.InvocationArgument;
import com.example.tests.orchestration.analysis.MethodDependencyAnalyzer;
import com.example.tests.orchestration.analysis.MethodDependencyGraph;
import com.example.tests.orchestration.analysis.MethodInvocation;
import com.example.tests.orchestration.analysis.MethodParameter;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Executes the per-method diff generation workflow when the --diff-method flag is active.
 */
public class DiffMethodGenerationRunner {

    private static final Logger LOGGER = Logger.getLogger(DiffMethodGenerationRunner.class.getName());

    private final Path projectRoot;
    private final LLMClient llmClient;
    private final int maxRetries;
    private final Duration requestDelay;
    private final Map<String, Object> requestOptions;
    private final JavaCompiler compiler;
    private final Path workingDirectory;
    private final Path testSourcesRoot;
    private final ResponseValidator responseValidator;
    private final GeneratedTestVerifier testVerifier;
    private final MethodDependencyAnalyzer methodDependencyAnalyzer;
    private static final Pattern VAR_PATTERN = Pattern.compile("\\bvar\\b");
    private long lastRequestAtNanos;

    public DiffMethodGenerationRunner(Path projectRoot,
                                      LLMClient llmClient,
                                      int maxRetries,
                                      Duration requestDelay,
                                      Map<String, Object> requestOptions) {
        this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot");
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.maxRetries = Math.max(0, maxRetries);
        this.requestDelay = requestDelay == null ? Duration.ZERO : requestDelay;
        this.requestOptions = requestOptions == null
                ? Map.of()
                : Collections.unmodifiableMap(new HashMap<>(requestOptions));
        this.compiler = ToolProvider.getSystemJavaCompiler();
        if (this.compiler == null) {
            throw new IllegalStateException("Java compiler is not available. Ensure a JDK is installed.");
        }
        this.workingDirectory = projectRoot.resolve("build").resolve("diff-method");
        this.testSourcesRoot = projectRoot.resolve(Paths.get("src", "test", "java"));
        this.responseValidator = new ResponseValidator();
        this.testVerifier = new GeneratedTestVerifier();
        this.methodDependencyAnalyzer = new MethodDependencyAnalyzer();
        this.lastRequestAtNanos = -1L;
    }

    public GenerationSummary execute(List<ClassMetadata> targets, List<ClassMetadata> discovered) throws IOException {
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(discovered, "discovered");
        Files.createDirectories(workingDirectory);
        Files.createDirectories(testSourcesRoot);
        List<Path> supportSources = ensureSupportSources();
        Map<String, ClassMetadata> metadataIndex = discovered.stream()
                .collect(Collectors.toMap(ClassMetadata::getQualifiedName, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        for (ClassMetadata metadata : discovered) {
            metadataIndex.putIfAbsent(metadata.getClassName(), metadata);
        }
        for (ClassMetadata target : targets) {
            metadataIndex.putIfAbsent(target.getQualifiedName(), target);
            metadataIndex.putIfAbsent(target.getClassName(), target);
        }
        List<ClassMetadata> discoveredList = new ArrayList<>(metadataIndex.values());
        MetadataTransformer metadataTransformer = new MetadataTransformer(discoveredList);
        List<MethodGenerationStatus> statuses = new ArrayList<>();
        for (ClassMetadata target : targets) {
            List<MethodMetadata> publicMethods = target.getMethods().stream()
                    .filter(MethodMetadata::isPublic)
                    .collect(Collectors.toList());
            if (publicMethods.isEmpty()) {
                LOGGER.info(() -> String.format(Locale.ENGLISH,
                        "Класс %s не содержит публичных методов для diff-генерации",
                        target.getQualifiedName()));
                continue;
            }
            String classContext = buildClassContext(target, discovered);
            LOGGER.info(() -> "class_api_context для " + target.getQualifiedName() + ":\n" + classContext);
            String testClassName = resolveTestClassName(target);
            Path finalFile = prepareFinalTestFile(target, testClassName);
            for (MethodMetadata method : publicMethods) {
                MethodGenerationStatus status = processMethod(target, method, classContext, supportSources, metadataIndex, discoveredList, metadataTransformer, finalFile, testClassName);
                statuses.add(status);
            }
        }
        return new GenerationSummary(statuses);
    }

    private Path prepareFinalTestFile(ClassMetadata owner, String testClassName) throws IOException {
        Path finalFile = resolveFinalFile(owner, testClassName);
        Files.createDirectories(finalFile.getParent());
        if (Files.notExists(finalFile)) {
            Files.writeString(finalFile, createSkeleton(owner.getPackageName(), testClassName), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
        }
        return finalFile;
    }

    private Path resolveFinalFile(ClassMetadata owner, String testClassName) {
        String packageName = owner.getPackageName();
        if (packageName == null || packageName.isBlank()) {
            return testSourcesRoot.resolve(testClassName + ".java");
        }
        Path packagePath = testSourcesRoot;
        for (String segment : packageName.split("\\.")) {
            if (!segment.isBlank()) {
                packagePath = packagePath.resolve(segment);
            }
        }
        return packagePath.resolve(testClassName + ".java");
    }

    private String resolveTestClassName(ClassMetadata owner) {
        String baseName = owner.getClassName();
        if (baseName == null || baseName.isBlank()) {
            return "GeneratedTest";
        }
        return baseName.endsWith("Test") ? baseName : baseName + "Test";
    }

    private MethodGenerationStatus processMethod(ClassMetadata owner,
                                                 MethodMetadata method,
                                                 String classContext,
                                                 List<Path> supportSources,
                                                 Map<String, ClassMetadata> metadataIndex,
                                                 List<ClassMetadata> discoveredList,
                                                 MetadataTransformer metadataTransformer,
                                                 Path finalFile,
                                                 String testClassName) throws IOException {
        List<String> feedback = new ArrayList<>();
        int iterations = 0;
        boolean compiled = false;
        List<String> lastErrors = List.of();
        List<String> previousCompilationErrors = List.of();
        List<Path> projectSources = collectCompilationSources(owner, metadataIndex);
        com.example.tests.generator.metadata.ClassMetadata promptMetadata = metadataTransformer.transform(owner);
        MethodDependencyInfo methodDependencies = inspectMethodDependencies(owner, method, metadataTransformer);
        while (iterations <= maxRetries) {
            iterations++;
            final int attempt = iterations;
            final int totalAttempts = maxRetries + 1;
            String prompt = buildPrompt(classContext, owner, method, feedback, metadataIndex, discoveredList,
                    methodDependencies, testClassName);
            LOGGER.info(() -> String.format(Locale.ENGLISH,
                    "Формирование diff-method запроса (%d/%d) для %s#%s",
                    attempt, totalAttempts, owner.getQualifiedName(), method.getName()));
            String response = sendPrompt(prompt);
            Optional<GeneratedSnippet> snippet = extractSnippet(response, method.getName());
            if (snippet.isEmpty()) {
                feedback = List.of(String.format(Locale.ENGLISH,
                        "LLM response did not contain a test method for %s#%s",
                        owner.getQualifiedName(), method.getName()));
                lastErrors = feedback;
                continue;
            }
            GeneratedSnippet generatedSnippet = snippet.get();
            if (containsVarUsage(generatedSnippet.methodSource())) {
                feedback = List.of("Не используй ключевое слово var. Объяви тип явно.");
                lastErrors = feedback;
                continue;
            }
            String currentSource = Files.exists(finalFile)
                    ? Files.readString(finalFile)
                    : createSkeleton(owner.getPackageName(), testClassName);
            currentSource = ensurePackageDeclaration(currentSource, owner.getPackageName());
            String candidateSource = mergeMethodIntoSource(generatedSnippet.methodSource(), currentSource, method, testClassName, finalFile);
            candidateSource = mergeImportsIntoSource(generatedSnippet.imports(), candidateSource, testClassName);
            GeneratedTestClass candidateClass = new GeneratedTestClass(owner.getPackageName(), testClassName, candidateSource);
            GeneratedTestClass verifiedClass = testVerifier.verify(candidateClass, promptMetadata, previousCompilationErrors);
            candidateSource = verifiedClass.getSourceCode();
            ValidationResult validationResult = responseValidator.validate(candidateSource, promptMetadata);
            candidateSource = validationResult.getSanitizedCode().orElse(candidateSource);
            candidateSource = ensurePackageDeclaration(candidateSource, owner.getPackageName());
            Files.createDirectories(finalFile.getParent());
            Files.writeString(finalFile, candidateSource, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            if (!validationResult.isValid()) {
                feedback = validationResult.getErrors();
                lastErrors = feedback;
                previousCompilationErrors = List.of();
                continue;
            }
            CompilationResult compilation = compileCandidate(finalFile, supportSources, projectSources);
            if (compilation.success()) {
                compiled = true;
                lastErrors = List.of();
                previousCompilationErrors = List.of();
                break;
            }
            feedback = compilation.errors();
            lastErrors = feedback;
            previousCompilationErrors = compilation.errors();
        }
        return new MethodGenerationStatus(owner.getQualifiedName(), method.getName(), compiled, iterations, lastErrors);
    }

    private MethodDependencyInfo inspectMethodDependencies(ClassMetadata owner,
                                                           MethodMetadata method,
                                                           MetadataTransformer metadataTransformer) {
        if (owner.getSourcePath() == null) {
            return MethodDependencyInfo.empty();
        }
        String analyzerMethodName = method.isConstructor() ? "<init>" : method.getName();
        try {
            MethodDependencyGraph graph = methodDependencyAnalyzer.analyze(owner.getSourcePath(),
                    owner.getQualifiedName(), analyzerMethodName);
            return MethodDependencyInfo.from(graph, owner, metadataTransformer);
        } catch (RuntimeException ex) {
            LOGGER.warning(() -> String.format(Locale.ENGLISH,
                    "Не удалось проанализировать зависимости метода %s#%s: %s",
                    owner.getQualifiedName(), method.getName(), ex.getMessage()));
            return MethodDependencyInfo.empty();
        }
    }

    private String buildPrompt(String classContext,
                               ClassMetadata owner,
                               MethodMetadata method,
                               List<String> previousErrors,
                               Map<String, ClassMetadata> metadataIndex,
                               List<ClassMetadata> discoveredList,
                               MethodDependencyInfo methodDependencies,
                               String testClassName) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Вы — помощник, который пишет модульные тесты на JUnit 5.\n");
        prompt.append("class_api_context = ").append(classContext).append('\n');
        prompt.append("target_method = ").append(describeMethod(owner, method)).append('\n');
        String apiReference = buildApiReference(owner, metadataIndex, discoveredList, methodDependencies);
        if (!apiReference.isBlank()) {
            prompt.append("available_api_signatures:\n");
            prompt.append(apiReference);
        }
        prompt.append(String.format(Locale.ENGLISH,
                "Сгенерируй ровно один тестовый метод внутри класса %s.\n",
                testClassName));
        prompt.append("Требования:\n");
        prompt.append("- Используй аннотацию @org.junit.jupiter.api.Test.\n");
        prompt.append("- В начале ответа перечисли необходимые import-операторы (каждый в формате 'import ...;'), затем оставь пустую строку и приведи ровно один тестовый метод.\n");
        prompt.append("- Пользуйся только типами и методами из available_api_signatures и стандартной библиотеки Java.\n");
        prompt.append("- Используй явные типы в объявлениях переменных, не применяй ключевое слово var.\n");
        prompt.append("- Используй короткие имена типов с необходимыми import-операторами; не оставляй fully-qualified имена в коде.\n");
        prompt.append(String.format(Locale.ENGLISH,
                "- Не добавляй объявление класса %s и не используй package.\n",
                testClassName));
        prompt.append("- Не используй Mockito, AssertJ, Hamcrest и другие внешние фреймворки.\n");
        prompt.append("- Для проверок используй конструкции вида if (... ) { throw new AssertionError(\"описание\"); }.\n");
        prompt.append("- Чтобы подменить зависимости, создавай простые анонимные реализации или реальные объекты с доступными конструкторами.\n");
        prompt.append("- Не используй пустые лямбда-выражения вида () -> {} вместо зависимостей; всегда реализуй интерфейсы через анонимные классы.\n");
        prompt.append("- Убедись, что тест содержит хотя бы одну проверку (assert* или явный бросок AssertionError).\n");
        prompt.append("- Создавай и используй только те типы и методы, которые перечислены в available_api_signatures.\n");
        prompt.append("- Тест должен однозначно компилироваться.\n");
        if (!previousErrors.isEmpty()) {
            prompt.append("Предыдущие ошибки компиляции:\n");
            for (String error : previousErrors) {
                prompt.append("- ").append(error).append('\n');
            }
        }
        prompt.append("Ответь только импортами и кодом метода.");
        return prompt.toString();
    }

    private String buildApiReference(ClassMetadata owner,
                                     Map<String, ClassMetadata> metadataIndex,
                                     List<ClassMetadata> discoveredList,
                                     MethodDependencyInfo methodDependencies) {
        StringBuilder builder = new StringBuilder();
        List<MethodMetadata> ownerMethods = owner.getMethods().stream()
                .filter(MethodMetadata::isPublic)
                .collect(Collectors.toList());
        if (!ownerMethods.isEmpty()) {
            builder.append("- Методы класса ").append(owner.getQualifiedName()).append(':').append(System.lineSeparator());
            for (MethodMetadata method : ownerMethods) {
                builder.append("  * ").append(describeMethodForReference(owner, method)).append(System.lineSeparator());
            }
        }

        Set<String> dependencies = new LinkedHashSet<>(owner.getDependencies());
        if (methodDependencies != null) {
            methodDependencies.domainTypes().stream()
                    .map(ClassMetadata::getQualifiedName)
                    .forEach(dependencies::add);
        }
        if (!dependencies.isEmpty()) {
            if (builder.length() > 0) {
                builder.append(System.lineSeparator());
            }
            builder.append("- Доступные зависимости: ").append(System.lineSeparator());
            for (String dependency : dependencies) {
                ClassMetadata metadata = metadataIndex.get(dependency);
                if (metadata == null) {
                    builder.append("  * ").append(dependency).append(" — описание не найдено").append(System.lineSeparator());
                    continue;
                }
                List<MethodMetadata> methods = metadata.getMethods().stream()
                        .filter(MethodMetadata::isPublic)
                        .collect(Collectors.toList());
                builder.append("  * ").append(metadata.getQualifiedName()).append(System.lineSeparator());
                if (methods.isEmpty()) {
                    builder.append("      (нет публичных методов)").append(System.lineSeparator());
                    continue;
                }
                for (MethodMetadata method : methods) {
                    builder.append("    - ").append(describeMethodForReference(metadata, method)).append(System.lineSeparator());
                }
            }
        }

        List<ClassMetadata> innerTypeMetadata = resolveInnerTypeMetadata(owner, discoveredList);
        if (!innerTypeMetadata.isEmpty()) {
            if (builder.length() > 0) {
                builder.append(System.lineSeparator());
            }
            builder.append("- Внутренние типы: ").append(System.lineSeparator());
            for (ClassMetadata metadata : innerTypeMetadata) {
                builder.append("  * ").append(metadata.getQualifiedName()).append(System.lineSeparator());
                List<MethodMetadata> methods = metadata.getMethods().stream()
                        .filter(MethodMetadata::isPublic)
                        .collect(Collectors.toList());
                if (methods.isEmpty()) {
                    builder.append("      (нет публичных методов)").append(System.lineSeparator());
                    continue;
                }
                for (MethodMetadata method : methods) {
                    builder.append("    - ").append(describeMethodForReference(metadata, method)).append(System.lineSeparator());
                }
            }
        }

        if (methodDependencies != null && !methodDependencies.standardTypes().isEmpty()) {
            if (builder.length() > 0) {
                builder.append(System.lineSeparator());
            }
            builder.append("- Стандартные классы Java, используемые методом: ")
                    .append(System.lineSeparator());
            for (String type : methodDependencies.standardTypes()) {
                builder.append("  * ").append(type).append(System.lineSeparator());
            }
        }

        return builder.toString();
    }

    private static final class MethodDependencyInfo {

        private final List<ClassMetadata> domainTypes;
        private final List<String> standardTypes;

        private MethodDependencyInfo(List<ClassMetadata> domainTypes, List<String> standardTypes) {
            this.domainTypes = Collections.unmodifiableList(new ArrayList<>(domainTypes));
            this.standardTypes = Collections.unmodifiableList(new ArrayList<>(standardTypes));
        }

        static MethodDependencyInfo empty() {
            return new MethodDependencyInfo(List.of(), List.of());
        }

        static MethodDependencyInfo from(MethodDependencyGraph graph,
                                         ClassMetadata owner,
                                         MetadataTransformer metadataTransformer) {
            if (graph == null) {
                return empty();
            }
            LinkedHashSet<ClassMetadata> domain = new LinkedHashSet<>();
            LinkedHashSet<String> standard = new LinkedHashSet<>();
            String ownerQualifiedName = owner.getQualifiedName();
            String ownerPackage = owner.getPackageName();

            for (MethodParameter parameter : graph.getParameters()) {
                registerType(parameter.getType(), ownerPackage, ownerQualifiedName, metadataTransformer, domain, standard);
            }
            for (DependencyNode dependency : graph.getDependencies()) {
                traverseDependency(dependency, ownerPackage, ownerQualifiedName, metadataTransformer, domain, standard);
            }
            for (MethodInvocation invocation : graph.getUnattachedInvocations()) {
                for (InvocationArgument argument : invocation.getArguments()) {
                    registerType(argument.getType(), ownerPackage, ownerQualifiedName, metadataTransformer, domain, standard);
                }
            }

            return new MethodDependencyInfo(new ArrayList<>(domain), new ArrayList<>(standard));
        }

        List<ClassMetadata> domainTypes() {
            return domainTypes;
        }

        List<String> standardTypes() {
            return standardTypes;
        }

        private static void traverseDependency(DependencyNode node,
                                               String ownerPackage,
                                               String ownerQualifiedName,
                                               MetadataTransformer metadataTransformer,
                                               Set<ClassMetadata> domain,
                                               Set<String> standard) {
            if (node == null) {
                return;
            }
            registerType(node.getType(), ownerPackage, ownerQualifiedName, metadataTransformer, domain, standard);
            registerType(node.getDeclaredType(), ownerPackage, ownerQualifiedName, metadataTransformer, domain, standard);
            for (InvocationArgument argument : node.getConstructorArguments()) {
                registerType(argument.getType(), ownerPackage, ownerQualifiedName, metadataTransformer, domain, standard);
            }
            for (MethodInvocation invocation : node.getMethodInvocations()) {
                for (InvocationArgument argument : invocation.getArguments()) {
                    registerType(argument.getType(), ownerPackage, ownerQualifiedName, metadataTransformer, domain, standard);
                }
            }
            for (DependencyNode child : node.getDependencies()) {
                traverseDependency(child, ownerPackage, ownerQualifiedName, metadataTransformer, domain, standard);
            }
        }

        private static void registerType(String rawType,
                                         String ownerPackage,
                                         String ownerQualifiedName,
                                         MetadataTransformer metadataTransformer,
                                         Set<ClassMetadata> domain,
                                         Set<String> standard) {
            if (rawType == null || rawType.isBlank()) {
                return;
            }
            for (String token : extractTypeTokens(rawType)) {
                if (token.isBlank()) {
                    continue;
                }
                Optional<ClassMetadata> resolved = metadataTransformer.resolveRawMetadata(token, ownerPackage);
                if (resolved.isPresent()) {
                    ClassMetadata metadata = resolved.get();
                    if (!metadata.getQualifiedName().equals(ownerQualifiedName)) {
                        domain.add(metadata);
                    }
                    continue;
                }
                StandardLibraryTypeResolver.resolve(token).ifPresent(standard::add);
            }
        }

        private static Set<String> extractTypeTokens(String rawType) {
            String cleaned = rawType.replace('<', ' ').replace('>', ' ')
                    .replace('(', ' ').replace(')', ' ')
                    .replace('[', ' ').replace(']', ' ')
                    .replace(',', ' ').replace('?', ' ');
            String[] parts = cleaned.split("\\s+");
            Set<String> tokens = new LinkedHashSet<>();
            for (String part : parts) {
                String token = normaliseToken(part);
                if (token.isEmpty()) {
                    continue;
                }
                char first = token.charAt(0);
                if (!Character.isLetter(first) || Character.isLowerCase(first)) {
                    continue;
                }
                tokens.add(token);
            }
            return tokens;
        }

        private static String normaliseToken(String value) {
            if (value == null) {
                return "";
            }
            String trimmed = value.trim();
            while (trimmed.endsWith("[]")) {
                trimmed = trimmed.substring(0, trimmed.length() - 2);
            }
            if (trimmed.endsWith("...")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
            while (trimmed.endsWith(".")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            if (trimmed.equalsIgnoreCase("null") || trimmed.equalsIgnoreCase("unknown")) {
                return "";
            }
            return trimmed;
        }
    }

    private String describeMethod(ClassMetadata owner, MethodMetadata method) {
        String parameters = method.getParameters().stream()
                .map(Parameter::toString)
                .collect(Collectors.joining(", "));
        String ownerName = owner.getClassName();
        if (parameters.isEmpty()) {
            parameters = "";
        }
        if (method.isConstructor()) {
            return ownerName + '(' + parameters + ')';
        }
        String qualifier = method.isStatic() ? "static " : "";
        return qualifier + method.getReturnType() + ' ' + ownerName + '.' + method.getName() + '(' + parameters + ')';
    }

    private String describeMethodForReference(ClassMetadata owner, MethodMetadata method) {
        String parameters = method.getParameters().stream()
                .map(Parameter::toString)
                .collect(Collectors.joining(", "));
        String ownerName = owner.getQualifiedName();
        if (method.isConstructor()) {
            return ownerName + '(' + parameters + ')';
        }
        String qualifier = method.isStatic() ? "static " : "";
        return qualifier + method.getReturnType() + ' ' + ownerName + '.' + method.getName() + '(' + parameters + ')';
    }

    private Optional<GeneratedSnippet> extractSnippet(String response, String methodName) {
        if (response == null || response.isBlank()) {
            return Optional.empty();
        }
        String code = extractCodeBlock(response).trim();
        if (code.isEmpty() || !code.contains(methodName + "(")) {
            return Optional.empty();
        }
        List<String> imports = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        for (String line : code.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("import ")) {
                imports.add(trimmed.endsWith(";") ? trimmed : trimmed + ';');
                continue;
            }
            builder.append(line).append(System.lineSeparator());
        }
        String methodSourceCandidate = builder.toString().trim();
        if (methodSourceCandidate.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> methodBlock;
        if (!methodSourceCandidate.contains("class ")) {
            methodBlock = Optional.of(methodSourceCandidate);
        } else {
            methodBlock = extractMethodBlock(methodSourceCandidate, methodName).map(String::trim);
        }
        return methodBlock.map(source -> new GeneratedSnippet(imports, source));
    }

    private String extractCodeBlock(String response) {
        int fenceStart = response.indexOf("```");
        if (fenceStart < 0) {
            return response;
        }
        int contentStart = fenceStart + 3;
        if (contentStart < response.length() && response.charAt(contentStart) == 'j') {
            int newline = response.indexOf('\n', contentStart);
            if (newline > contentStart) {
                contentStart = newline + 1;
            }
        }
        int fenceEnd = response.indexOf("```", contentStart);
        if (fenceEnd < 0) {
            return response.substring(contentStart);
        }
        return response.substring(contentStart, fenceEnd);
    }

    private Optional<String> extractMethodBlock(String source, String methodName) {
        String[] lines = source.split("\\R");
        StringBuilder builder = new StringBuilder();
        boolean capturing = false;
        int braceBalance = 0;
        boolean seenBody = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!capturing && isMethodDeclarationLine(line, methodName)) {
                int start = i;
                while (start > 0 && lines[start - 1].trim().startsWith("@")) {
                    start--;
                }
                for (int j = start; j <= i; j++) {
                    builder.append(lines[j]).append(System.lineSeparator());
                    braceBalance += countOccurrences(lines[j], '{');
                    braceBalance -= countOccurrences(lines[j], '}');
                    if (lines[j].contains("{")) {
                        seenBody = true;
                    }
                }
                capturing = true;
                continue;
            }
            if (capturing) {
                builder.append(line).append(System.lineSeparator());
                braceBalance += countOccurrences(line, '{');
                braceBalance -= countOccurrences(line, '}');
                if (line.contains("{")) {
                    seenBody = true;
                }
                if (seenBody && braceBalance <= 0) {
                    return Optional.of(builder.toString().stripTrailing());
                }
            }
        }
        if (capturing) {
            return Optional.of(builder.toString().stripTrailing());
        }
        return Optional.empty();
    }

    private boolean isMethodDeclarationLine(String line, String methodName) {
        String trimmed = line.trim();
        if (trimmed.startsWith("//") || trimmed.contains(";")) {
            return false;
        }
        int nameIndex = trimmed.indexOf(methodName + "(");
        if (nameIndex < 0) {
            return false;
        }
        return nameIndex + methodName.length() + 1 < trimmed.length();
    }

    private int countOccurrences(String value, char target) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == target) {
                count++;
            }
        }
        return count;
    }

    private String mergeMethodIntoSource(String methodSource,
                                         String currentSource,
                                         MethodMetadata method,
                                         String testClassName,
                                         Path finalFile) {
        int insertionPoint;
        String sanitized = methodSource.strip();
        String withoutExisting = currentSource;
        Optional<String> declaredMethodName = extractDeclaredMethodName(sanitized);
        if (declaredMethodName.isPresent()) {
            withoutExisting = removeExistingMethod(withoutExisting, declaredMethodName.get());
        }
        if (!method.getName().isBlank()) {
            withoutExisting = removeExistingMethod(withoutExisting, method.getName());
        }
        insertionPoint = withoutExisting.lastIndexOf('}');
        if (insertionPoint < 0) {
            String fileName = finalFile.getFileName() == null
                    ? testClassName + ".java"
                    : finalFile.getFileName().toString();
            throw new IllegalStateException(fileName + " is malformed: missing class terminator");
        }
        String indented = indentMethod(sanitized);
        StringBuilder updated = new StringBuilder(withoutExisting);
        String separator = System.lineSeparator();
        String insertion = separator + indented + separator;
        updated.insert(insertionPoint, insertion);
        return updated.toString();
    }

    private String indentMethod(String methodSource) {
        String[] lines = methodSource.split("\\R");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            builder.append("    ").append(lines[i]);
            if (i < lines.length - 1) {
                builder.append(System.lineSeparator());
            }
        }
        return builder.toString();
    }

    private String removeExistingMethod(String source, String methodName) {
        if (methodName == null || methodName.isBlank()) {
            return source;
        }
        String updated = source;
        while (true) {
            Optional<String> existingBlock = extractMethodBlock(updated, methodName);
            if (existingBlock.isEmpty()) {
                break;
            }
            String block = existingBlock.get();
            int index = updated.indexOf(block);
            if (index < 0) {
                break;
            }
            int removalStart = index;
            while (removalStart > 0) {
                char previous = updated.charAt(removalStart - 1);
                if (previous == '\n') {
                    removalStart--;
                    if (removalStart > 0 && updated.charAt(removalStart - 1) == '\r') {
                        removalStart--;
                    }
                    break;
                }
                if (!Character.isWhitespace(previous)) {
                    break;
                }
                removalStart--;
            }
            int end = index + block.length();
            while (end < updated.length()) {
                char current = updated.charAt(end);
                if (current == '\r') {
                    end++;
                    if (end < updated.length() && updated.charAt(end) == '\n') {
                        end++;
                    }
                    break;
                }
                if (current == '\n') {
                    end++;
                    break;
                }
                if (!Character.isWhitespace(current)) {
                    break;
                }
                end++;
            }
            updated = updated.substring(0, removalStart) + updated.substring(end);
        }
        return updated;
    }

    private Optional<String> extractDeclaredMethodName(String methodSource) {
        if (methodSource == null) {
            return Optional.empty();
        }
        String sanitized = methodSource.strip();
        if (sanitized.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder signatureBuilder = new StringBuilder();
        String[] lines = sanitized.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("@")) {
                continue;
            }
            signatureBuilder.append(trimmed).append(' ');
            if (trimmed.contains("(") || trimmed.contains("{")) {
                break;
            }
        }
        String signature = signatureBuilder.toString().trim();
        if (signature.isEmpty()) {
            return Optional.empty();
        }
        int parenIndex = signature.indexOf('(');
        if (parenIndex < 0) {
            return Optional.empty();
        }
        String beforeParen = signature.substring(0, parenIndex).trim();
        if (beforeParen.isEmpty()) {
            return Optional.empty();
        }
        String[] tokens = beforeParen.split("\\s+");
        if (tokens.length == 0) {
            return Optional.empty();
        }
        String candidate = tokens[tokens.length - 1];
        if (!isValidJavaIdentifier(candidate)) {
            return Optional.empty();
        }
        return Optional.of(candidate);
    }

    private boolean isValidJavaIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return false;
        }
        if (!Character.isJavaIdentifierStart(identifier.charAt(0))) {
            return false;
        }
        for (int i = 1; i < identifier.length(); i++) {
            if (!Character.isJavaIdentifierPart(identifier.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private String createSkeleton(String packageName, String testClassName) {
        StringBuilder builder = new StringBuilder();
        if (packageName != null && !packageName.isBlank()) {
            builder.append("package ")
                    .append(packageName)
                    .append(';')
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());
        }
        builder.append("import org.junit.jupiter.api.Test;")
                .append(System.lineSeparator())
                .append(System.lineSeparator())
                .append("public class ")
                .append(testClassName)
                .append(" {")
                .append(System.lineSeparator())
                .append(System.lineSeparator())
                .append('}')
                .append(System.lineSeparator());
        return builder.toString();
    }

    private String ensurePackageDeclaration(String source, String packageName) {
        if (packageName == null || packageName.isBlank()) {
            return source;
        }
        Pattern packagePattern = Pattern.compile("(?m)^\\s*package\\s+([^;]+)\\s*;\\s*$");
        Matcher matcher = packagePattern.matcher(source);
        if (matcher.find()) {
            String existing = matcher.group(1).trim();
            if (existing.equals(packageName)) {
                return source;
            }
            return matcher.replaceFirst("package " + packageName + ';');
        }
        return "package " + packageName + ';' + System.lineSeparator() + System.lineSeparator() + source;
    }

    private String mergeImportsIntoSource(Collection<String> newImports, String source, String testClassName) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        String[] lines = source.split("\\R", -1);
        int classLineIndex = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("class " + testClassName)) {
                classLineIndex = i;
                break;
            }
        }
        if (classLineIndex < 0) {
            return source;
        }
        Set<String> mergedImports = new TreeSet<>();
        List<String> headerLines = new ArrayList<>();
        for (int i = 0; i < classLineIndex; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.startsWith("import ")) {
                mergedImports.add(trimmed.endsWith(";") ? trimmed : trimmed + ';');
                continue;
            }
            headerLines.add(lines[i]);
        }
        if (newImports != null) {
            for (String importLine : newImports) {
                if (importLine == null) {
                    continue;
                }
                String trimmed = importLine.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (!trimmed.startsWith("import ")) {
                    if (!trimmed.endsWith(";")) {
                        trimmed = "import " + trimmed + ';';
                    } else {
                        trimmed = "import " + trimmed.substring(0, trimmed.length() - 1).trim() + ';';
                    }
                } else if (!trimmed.endsWith(";")) {
                    trimmed = trimmed + ';';
                }
                mergedImports.add(trimmed);
            }
        }
        mergedImports.add("import org.junit.jupiter.api.Test;");

        while (!headerLines.isEmpty() && headerLines.get(headerLines.size() - 1).trim().isEmpty()) {
            headerLines.remove(headerLines.size() - 1);
        }

        StringBuilder builder = new StringBuilder();
        for (String line : headerLines) {
            builder.append(line).append(System.lineSeparator());
        }
        if (!headerLines.isEmpty()) {
            builder.append(System.lineSeparator());
        }
        for (String importLine : mergedImports) {
            builder.append(importLine).append(System.lineSeparator());
        }
        builder.append(System.lineSeparator());
        for (int i = classLineIndex; i < lines.length; i++) {
            builder.append(lines[i]);
            if (i < lines.length - 1) {
                builder.append(System.lineSeparator());
            }
        }
        if (!source.endsWith(System.lineSeparator())) {
            builder.append(System.lineSeparator());
        }
        return builder.toString();
    }

    private boolean containsVarUsage(String methodSource) {
        if (methodSource == null) {
            return false;
        }
        return VAR_PATTERN.matcher(methodSource).find();
    }

    private static final class GeneratedSnippet {
        private final List<String> imports;
        private final String methodSource;

        private GeneratedSnippet(List<String> imports, String methodSource) {
            this.imports = imports == null ? List.of() : List.copyOf(imports);
            this.methodSource = methodSource;
        }

        private List<String> imports() {
            return imports;
        }

        private String methodSource() {
            return methodSource;
        }
    }

    private List<ClassMetadata> resolveInnerTypeMetadata(ClassMetadata target, List<ClassMetadata> discovered) {
        String qualifiedPrefixDot = target.getQualifiedName() + '.';
        String qualifiedPrefixDollar = target.getQualifiedName() + '$';
        return discovered.stream()
                .filter(candidate -> {
                    String qualifiedName = candidate.getQualifiedName();
                    return qualifiedName.startsWith(qualifiedPrefixDot) || qualifiedName.startsWith(qualifiedPrefixDollar);
                })
                .sorted(Comparator.comparing(ClassMetadata::getQualifiedName))
                .collect(Collectors.toList());
    }

    private List<Path> ensureSupportSources() throws IOException {
        List<Path> sources = new ArrayList<>();
        Path stubsRoot = workingDirectory.resolve("stubs");
        Path junitTest = stubsRoot.resolve(Paths.get("org", "junit", "jupiter", "api", "Test.java"));
        String testStub = "package org.junit.jupiter.api;" + System.lineSeparator()
                + System.lineSeparator()
                + "import java.lang.annotation.ElementType;" + System.lineSeparator()
                + "import java.lang.annotation.Retention;" + System.lineSeparator()
                + "import java.lang.annotation.RetentionPolicy;" + System.lineSeparator()
                + "import java.lang.annotation.Target;" + System.lineSeparator()
                + System.lineSeparator()
                + "@Retention(RetentionPolicy.RUNTIME)" + System.lineSeparator()
                + "@Target(ElementType.METHOD)" + System.lineSeparator()
                + "public @interface Test {" + System.lineSeparator()
                + "}" + System.lineSeparator();
        sources.add(writeStubSource(junitTest, testStub));

        Path assertions = stubsRoot.resolve(Paths.get("org", "junit", "jupiter", "api", "Assertions.java"));
        String assertionsStub = "package org.junit.jupiter.api;" + System.lineSeparator()
                + System.lineSeparator()
                + "public final class Assertions {" + System.lineSeparator()
                + "    private Assertions() {" + System.lineSeparator()
                + "    }" + System.lineSeparator()
                + System.lineSeparator()
                + "    public static void assertTrue(boolean condition) {" + System.lineSeparator()
                + "        if (!condition) {" + System.lineSeparator()
                + "            throw new AssertionError(\"Condition expected to be true\");" + System.lineSeparator()
                + "        }" + System.lineSeparator()
                + "    }" + System.lineSeparator()
                + System.lineSeparator()
                + "    public static void assertTrue(boolean condition, String message) {" + System.lineSeparator()
                + "        if (!condition) {" + System.lineSeparator()
                + "            throw new AssertionError(message);" + System.lineSeparator()
                + "        }" + System.lineSeparator()
                + "    }" + System.lineSeparator()
                + System.lineSeparator()
                + "    public static void assertFalse(boolean condition) {" + System.lineSeparator()
                + "        if (condition) {" + System.lineSeparator()
                + "            throw new AssertionError(\"Condition expected to be false\");" + System.lineSeparator()
                + "        }" + System.lineSeparator()
                + "    }" + System.lineSeparator()
                + System.lineSeparator()
                + "    public static void assertEquals(Object expected, Object actual) {" + System.lineSeparator()
                + "        if (expected == null ? actual != null : !expected.equals(actual)) {" + System.lineSeparator()
                + "            throw new AssertionError(\"Values are not equal\");" + System.lineSeparator()
                + "        }" + System.lineSeparator()
                + "    }" + System.lineSeparator()
                + System.lineSeparator()
                + "    public static void assertEquals(Object expected, Object actual, String message) {" + System.lineSeparator()
                + "        if (expected == null ? actual != null : !expected.equals(actual)) {" + System.lineSeparator()
                + "            throw new AssertionError(message);" + System.lineSeparator()
                + "        }" + System.lineSeparator()
                + "    }" + System.lineSeparator()
                + System.lineSeparator()
                + "    public static void assertNotNull(Object value) {" + System.lineSeparator()
                + "        if (value == null) {" + System.lineSeparator()
                + "            throw new AssertionError(\"Value expected to be non-null\");" + System.lineSeparator()
                + "        }" + System.lineSeparator()
                + "    }" + System.lineSeparator()
                + System.lineSeparator()
                + "    public static void assertNotNull(Object value, String message) {" + System.lineSeparator()
                + "        if (value == null) {" + System.lineSeparator()
                + "            throw new AssertionError(message);" + System.lineSeparator()
                + "        }" + System.lineSeparator()
                + "    }" + System.lineSeparator()
                + System.lineSeparator()
                + "    public static void assertNull(Object value) {" + System.lineSeparator()
                + "        if (value != null) {" + System.lineSeparator()
                + "            throw new AssertionError(\"Value expected to be null\");" + System.lineSeparator()
                + "        }" + System.lineSeparator()
                + "    }" + System.lineSeparator()
                + System.lineSeparator()
                + "    public static void assertNull(Object value, String message) {" + System.lineSeparator()
                + "        if (value != null) {" + System.lineSeparator()
                + "            throw new AssertionError(message);" + System.lineSeparator()
                + "        }" + System.lineSeparator()
                + "    }" + System.lineSeparator()
                + System.lineSeparator()
                + "    public static void fail(String message) {" + System.lineSeparator()
                + "        throw new AssertionError(message);" + System.lineSeparator()
                + "    }" + System.lineSeparator()
                + "}" + System.lineSeparator();
        sources.add(writeStubSource(assertions, assertionsStub));
        return sources;
    }

    private List<Path> collectCompilationSources(ClassMetadata owner, Map<String, ClassMetadata> metadataIndex) {
        LinkedHashSet<Path> sources = new LinkedHashSet<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        Deque<ClassMetadata> queue = new ArrayDeque<>();
        queue.add(owner);
        seen.add(owner.getQualifiedName());
        seen.add(owner.getClassName());
        while (!queue.isEmpty()) {
            ClassMetadata current = queue.removeFirst();
            addSourcePath(sources, current.getSourcePath());
            for (String dependency : current.getDependencies()) {
                if (dependency == null || dependency.isBlank()) {
                    continue;
                }
                ClassMetadata dependencyMetadata = metadataIndex.get(dependency);
                if (dependencyMetadata == null) {
                    continue;
                }
                String qualified = dependencyMetadata.getQualifiedName();
                if (seen.add(qualified)) {
                    queue.add(dependencyMetadata);
                }
                seen.add(dependencyMetadata.getClassName());
            }
        }
        return new ArrayList<>(sources);
    }

    private void addSourcePath(Set<Path> collector, Path path) {
        if (path == null) {
            return;
        }
        Path absolute = path.toAbsolutePath().normalize();
        if (Files.exists(absolute)) {
            collector.add(absolute);
        }
    }

    private Path writeStubSource(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        return file;
    }

    private CompilationResult compileCandidate(Path sourceFile, List<Path> supportSources, List<Path> projectSources) throws IOException {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<String> options = buildCompilerOptions();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            List<Path> compilationPaths = new ArrayList<>(supportSources);
            compilationPaths.addAll(projectSources);
            compilationPaths.add(sourceFile);
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(
                    compilationPaths.stream().map(Path::toFile).collect(Collectors.toList()));
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);
            boolean success = Boolean.TRUE.equals(task.call());
            List<String> errors = diagnostics.getDiagnostics().stream()
                    .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                    .map(diagnostic -> formatDiagnostic(diagnostic, sourceFile))
                    .collect(Collectors.toList());
            if (!errors.isEmpty()) {
                success = false;
            }
            return new CompilationResult(success, errors);
        }
    }

    private List<String> buildCompilerOptions() {
        String classpath = System.getProperty("java.class.path", "");
        List<String> options = new ArrayList<>();
        options.add("-proc:none");
        if (!classpath.isBlank()) {
            options.add("-classpath");
            options.add(classpath);
        }
        return options;
    }

    private String formatDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic, Path sourceFile) {
        String sourceName;
        if (diagnostic.getSource() != null) {
            sourceName = Paths.get(diagnostic.getSource().toUri()).getFileName().toString();
        } else {
            sourceName = sourceFile.getFileName().toString();
        }
        return String.format(Locale.ENGLISH, "%s:%d:%d %s",
                sourceName,
                diagnostic.getLineNumber(),
                diagnostic.getColumnNumber(),
                diagnostic.getMessage(Locale.ENGLISH));
    }

    private String buildClassContext(ClassMetadata target, List<ClassMetadata> discovered) {
        String indent = "  ";
        StringBuilder builder = new StringBuilder();
        builder.append('{').append(System.lineSeparator());
        builder.append(indent).append("\"qualified_name\": \"")
                .append(escapeJson(target.getQualifiedName())).append("\",").append(System.lineSeparator());
        builder.append(indent).append("\"package\": \"")
                .append(escapeJson(target.getPackageName())).append("\",").append(System.lineSeparator());
        builder.append(indent).append("\"imports\": ")
                .append(toJsonArray(target.getImports())).append(',').append(System.lineSeparator());
        builder.append(indent).append("\"dependencies\": ")
                .append(toJsonArray(target.getDependencies())).append(',').append(System.lineSeparator());
        builder.append(indent).append("\"inner_types\": ")
                .append(toJsonArray(resolveInnerTypes(target, discovered))).append(',').append(System.lineSeparator());
        builder.append(indent).append("\"methods\": [").append(System.lineSeparator());
        List<MethodMetadata> methods = target.getMethods().stream()
                .filter(MethodMetadata::isPublic)
                .collect(Collectors.toList());
        for (int i = 0; i < methods.size(); i++) {
            MethodMetadata method = methods.get(i);
            builder.append(indent).append(indent).append('{').append(System.lineSeparator());
            builder.append(indent).append(indent).append(indent).append("\"name\": \"")
                    .append(escapeJson(method.getName())).append("\",").append(System.lineSeparator());
            builder.append(indent).append(indent).append(indent).append("\"return_type\": \"")
                    .append(escapeJson(method.getReturnType())).append("\",").append(System.lineSeparator());
            builder.append(indent).append(indent).append(indent).append("\"constructor\": ")
                    .append(method.isConstructor()).append(',').append(System.lineSeparator());
            builder.append(indent).append(indent).append(indent).append("\"static\": ")
                    .append(method.isStatic()).append(',').append(System.lineSeparator());
            builder.append(indent).append(indent).append(indent).append("\"annotations\": ")
                    .append(toJsonArray(method.getAnnotations())).append(',').append(System.lineSeparator());
            builder.append(indent).append(indent).append(indent).append("\"parameters\": [");
            List<Parameter> parameters = method.getParameters();
            if (!parameters.isEmpty()) {
                builder.append(System.lineSeparator());
                for (int j = 0; j < parameters.size(); j++) {
                    Parameter parameter = parameters.get(j);
                    builder.append(indent).append(indent).append(indent).append(indent).append('{')
                            .append("\"type\": \"").append(escapeJson(parameter.getType())).append("\",")
                            .append(" \"name\": \"").append(escapeJson(parameter.getName())).append("\"}");
                    if (j < parameters.size() - 1) {
                        builder.append(',');
                    }
                    builder.append(System.lineSeparator());
                }
                builder.append(indent).append(indent).append(indent).append(']');
            } else {
                builder.append(']');
            }
            builder.append(',').append(System.lineSeparator());
            builder.append(indent).append(indent).append(indent).append("\"signature\": \"")
                    .append(escapeJson(describeMethod(target, method))).append("\"").append(System.lineSeparator());
            builder.append(indent).append(indent).append('}');
            if (i < methods.size() - 1) {
                builder.append(',');
            }
            builder.append(System.lineSeparator());
        }
        builder.append(indent).append(']').append(System.lineSeparator());
        builder.append('}');
        return builder.toString();
    }

    private List<String> resolveInnerTypes(ClassMetadata target, List<ClassMetadata> discovered) {
        String prefix = target.getClassName() + '.';
        return discovered.stream()
                .map(ClassMetadata::getClassName)
                .filter(name -> name.startsWith(prefix))
                .sorted()
                .collect(Collectors.toList());
    }

    private String toJsonArray(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        Set<String> unique = values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (unique.isEmpty()) {
            return "[]";
        }
        List<String> sorted = new ArrayList<>(unique);
        sorted.sort(Comparator.naturalOrder());
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < sorted.size(); i++) {
            builder.append('"').append(escapeJson(sorted.get(i))).append('"');
            if (i < sorted.size() - 1) {
                builder.append(',').append(' ');
            }
        }
        builder.append(']');
        return builder.toString();
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\' || ch == '"') {
                escaped.append('\\');
            }
            escaped.append(ch);
        }
        return escaped.toString();
    }

    private String sendPrompt(String prompt) {
        applyRequestDelay();
        String response = llmClient.sendPrompt(prompt, requestOptions);
        lastRequestAtNanos = System.nanoTime();
        return response;
    }

    private void applyRequestDelay() {
        if (requestDelay.isZero() || lastRequestAtNanos < 0) {
            return;
        }
        long elapsed = System.nanoTime() - lastRequestAtNanos;
        long required = requestDelay.toNanos();
        long remaining = required - elapsed;
        if (remaining <= 0) {
            return;
        }
        long millis = remaining / 1_000_000L;
        int nanos = (int) (remaining % 1_000_000L);
        try {
            Thread.sleep(millis, nanos);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting between prompts", ex);
        }
    }

    private record CompilationResult(boolean success, List<String> errors) {
    }

    public record MethodGenerationStatus(String className,
                                         String methodName,
                                         boolean compiled,
                                         int iterations,
                                         List<String> lastErrors) {
        public MethodGenerationStatus {
            lastErrors = lastErrors == null ? List.of() : List.copyOf(lastErrors);
        }
    }

    public record GenerationSummary(List<MethodGenerationStatus> methods) {
        public GenerationSummary {
            methods = methods == null ? List.of() : List.copyOf(methods);
        }

        public void printSummary() {
            if (methods.isEmpty()) {
                System.out.println("Дифф-режим: нет публичных методов для обработки.");
                return;
            }
            System.out.println("Дифф-режим: результаты генерации по методам:");
            for (MethodGenerationStatus status : methods) {
                String outcome = status.compiled() ? "успех" : "ошибка";
                System.out.printf(Locale.ENGLISH, "- %s#%s: %s после %d попыток%n",
                        status.className(), status.methodName(), outcome, status.iterations());
                if (!status.lastErrors().isEmpty()) {
                    for (String error : status.lastErrors()) {
                        System.out.println("    * " + error);
                    }
                }
            }
        }
    }
}
