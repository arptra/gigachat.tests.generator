package com.example.tests.generator.diff;

import com.example.agent.providers.LLMClient;
import com.example.tests.generator.model.ClassMetadata;
import com.example.tests.generator.model.MethodMetadata;
import com.example.tests.generator.model.MethodMetadata.Parameter;

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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
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
    private final Path candidateFile;
    private final Path finalFile;
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
        this.candidateFile = workingDirectory.resolve("GeneratedTestCandidate.java");
        this.finalFile = workingDirectory.resolve("FinalGeneratedTest.java");
        this.lastRequestAtNanos = -1L;
    }

    public GenerationSummary execute(List<ClassMetadata> targets, List<ClassMetadata> discovered) throws IOException {
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(discovered, "discovered");
        Files.createDirectories(workingDirectory);
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
            for (MethodMetadata method : publicMethods) {
                MethodGenerationStatus status = processMethod(target, method, classContext);
                statuses.add(status);
            }
        }
        return new GenerationSummary(statuses);
    }

    private MethodGenerationStatus processMethod(ClassMetadata owner,
                                                 MethodMetadata method,
                                                 String classContext) throws IOException {
        List<String> feedback = new ArrayList<>();
        int iterations = 0;
        boolean compiled = false;
        List<String> lastErrors = List.of();
        while (iterations <= maxRetries) {
            iterations++;
            String prompt = buildPrompt(classContext, owner, method, feedback);
            LOGGER.info(() -> String.format(Locale.ENGLISH,
                    "Формирование diff-method запроса (%d/%d) для %s#%s",
                    iterations, maxRetries + 1, owner.getQualifiedName(), method.getName()));
            String response = sendPrompt(prompt);
            Optional<String> methodSource = extractMethod(response, method.getName());
            if (methodSource.isEmpty()) {
                feedback = List.of(String.format(Locale.ENGLISH,
                        "LLM response did not contain a test method for %s#%s",
                        owner.getQualifiedName(), method.getName()));
                lastErrors = feedback;
                continue;
            }
            String currentSource = Files.exists(finalFile)
                    ? Files.readString(finalFile)
                    : createSkeleton();
            String candidateSource = mergeMethodIntoSource(methodSource.get(), currentSource);
            Files.writeString(candidateFile, candidateSource, StandardCharsets.UTF_8);
            CompilationResult compilation = compileCandidate(candidateFile);
            if (compilation.success()) {
                Files.writeString(finalFile, candidateSource, StandardCharsets.UTF_8);
                compiled = true;
                lastErrors = List.of();
                break;
            }
            feedback = compilation.errors();
            lastErrors = feedback;
        }
        return new MethodGenerationStatus(owner.getQualifiedName(), method.getName(), compiled, iterations, lastErrors);
    }

    private String buildPrompt(String classContext,
                               ClassMetadata owner,
                               MethodMetadata method,
                               List<String> previousErrors) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Вы — помощник, который пишет модульные тесты на JUnit 5.\n");
        prompt.append("class_api_context = ").append(classContext).append('\n');
        prompt.append("target_method = ").append(describeMethod(owner, method)).append('\n');
        prompt.append("Сгенерируй ровно один тестовый метод внутри класса FinalGeneratedTest.\n");
        prompt.append("Требования:\n");
        prompt.append("- Используй аннотацию @org.junit.jupiter.api.Test.\n");
        prompt.append("- Не включай объявление класса, только метод.\n");
        prompt.append("- Тест должен однозначно компилироваться.\n");
        if (!previousErrors.isEmpty()) {
            prompt.append("Предыдущие ошибки компиляции:\n");
            for (String error : previousErrors) {
                prompt.append("- ").append(error).append('\n');
            }
        }
        prompt.append("Ответь только кодом метода.");
        return prompt.toString();
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

    private Optional<String> extractMethod(String response, String methodName) {
        if (response == null || response.isBlank()) {
            return Optional.empty();
        }
        String code = extractCodeBlock(response).trim();
        if (code.isEmpty() || !code.contains(methodName + "(")) {
            return Optional.empty();
        }
        if (!code.contains("class ")) {
            return Optional.of(code.trim());
        }
        return extractMethodBlock(code, methodName).map(String::trim);
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

    private String mergeMethodIntoSource(String methodSource, String currentSource) {
        if (containsMethod(currentSource, methodSource)) {
            return currentSource;
        }
        int insertionPoint = currentSource.lastIndexOf('}');
        if (insertionPoint < 0) {
            throw new IllegalStateException("FinalGeneratedTest.java is malformed: missing class terminator");
        }
        String indented = indentMethod(methodSource.strip());
        StringBuilder updated = new StringBuilder(currentSource);
        String separator = System.lineSeparator();
        String insertion = separator + indented + separator;
        updated.insert(insertionPoint, insertion);
        return updated.toString();
    }

    private boolean containsMethod(String source, String methodSource) {
        String signature = extractSignatureLine(methodSource);
        return !signature.isEmpty() && source.contains(signature);
    }

    private String extractSignatureLine(String methodSource) {
        for (String line : methodSource.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("@")) {
                continue;
            }
            return trimmed;
        }
        return "";
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

    private String createSkeleton() {
        return "import org.junit.jupiter.api.Test;" + System.lineSeparator()
                + System.lineSeparator()
                + "public class FinalGeneratedTest {" + System.lineSeparator()
                + System.lineSeparator()
                + "}" + System.lineSeparator();
    }

    private CompilationResult compileCandidate(Path sourceFile) throws IOException {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<String> options = buildCompilerOptions();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(List.of(sourceFile.toFile()));
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
