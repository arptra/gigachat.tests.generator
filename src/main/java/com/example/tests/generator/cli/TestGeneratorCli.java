package com.example.tests.generator.cli;

import com.example.agent.providers.GigaChatCertificateClient;
import com.example.agent.providers.GigachatLLMClient;
import com.example.agent.providers.LLMClient;
import com.example.tests.generator.analysis.DependencyDocumentation;
import com.example.tests.generator.analysis.TestDependencyDocumentationBuilder;
import com.example.tests.generator.metadata.MetadataTransformer;
import com.example.tests.generator.metadata.RelatedTypeMetadata;
import com.example.tests.generator.model.ClassMetadata;
import com.example.tests.generator.method.GigachatMethodMockValidationService;
import com.example.tests.generator.method.MethodMockGenerator;
import com.example.tests.generator.method.MethodMockPlan;
import com.example.tests.generator.method.MethodMockValidationService;
import com.example.tests.generator.method.MockSnippet;
import com.example.tests.generator.method.MockTemplateRepository;
import com.example.tests.generator.method.TargetClassAnalyzer;
import com.example.tests.generator.method.rules.MockRule;
import com.example.tests.generator.method.rules.NewObjectInvocationRule;
import com.example.tests.generator.method.rules.StaticVoidInvocationRule;
import com.example.tests.generator.pipeline.GeneratedTestClass;
import com.example.tests.generator.pipeline.GenerationReport;
import com.example.tests.generator.pipeline.TestGenerationPipeline;
import com.example.tests.generator.project.ProjectLayout;
import com.example.tests.generator.project.ProjectLayoutResolver;
import com.example.tests.generator.prompt.PromptBuilder;
import com.example.tests.generator.scanner.ProjectScanner;
import com.example.tests.generator.validate.ResponseValidator;
import com.example.tests.generator.validate.TestCodeParser;
import com.example.tests.generator.validate.ValidationResult;
import com.example.tests.generator.verification.GeneratedTestVerifier;
import com.example.tests.generator.verification.SignatureComplianceVerifier;
import com.example.tests.generator.config.GigachatClientConfig;
import com.example.tests.generator.config.GigachatClientProperties;
import com.example.tests.generator.util.LoggingConfigurator;
import com.example.tests.orchestration.TestFixIterationCoordinator;
import com.example.tests.orchestration.config.FixIterationExecutionSettings;
import com.example.tests.orchestration.execution.GradleTestSuiteRunner;
import com.example.tests.orchestration.execution.TestRunRequest;
import com.example.tests.orchestration.fix.GigachatResponseParser;
import com.example.tests.orchestration.analysis.MethodDependencyAnalyzer;
import com.example.tests.orchestration.fix.TestFixApplier;
import com.example.tests.orchestration.gigachat.GigachatFixGateway;
import com.example.tests.orchestration.gigachat.GigachatFixRequestBuilder;
import com.example.tests.orchestration.gigachat.PromptClient;
import com.example.tests.orchestration.gigachat.TestContextSnapshot;
import com.example.tests.orchestration.loop.FixIterationLoopHandler;
import com.example.tests.orchestration.reporting.TestFailureCollector;
import com.example.tests.orchestration.verification.TestSignatureVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.logging.Logger;

/**
 * Entry point for the Gigachat powered unit test generator.
 */
public final class TestGeneratorCli {

    private static final Logger LOGGER = Logger.getLogger(TestGeneratorCli.class.getName());

    public static void main(String[] args) {
        try {
            CliArguments arguments = CliArguments.parse(args);
            LoggingConfigurator.configure(arguments.infoLogging());
            new TestGeneratorCli().run(arguments);
        } catch (CliArguments.HelpRequestedException ignored) {
            CliArguments.printUsage();
        } catch (Exception exception) {
            System.err.println("Generation failed: " + exception.getMessage());
            exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private void run(CliArguments arguments) throws IOException {
        Path projectRoot = arguments.projectRoot();
        ProjectLayout projectLayout = ProjectLayoutResolver.detect(projectRoot);
        ProjectScanner scanner = new ProjectScanner(projectRoot, projectLayout);
        PromptBuilder promptBuilder = new PromptBuilder();
        ResponseValidator responseValidator = new ResponseValidator();
        TestCodeParser codeParser = new TestCodeParser();
        TestGenerationPipeline pipeline = new TestGenerationPipeline(projectRoot, projectLayout);
        GeneratedTestVerifier testVerifier = new GeneratedTestVerifier();

        boolean infoLogging = arguments.infoLogging();
        boolean focusDependencies = arguments.focusDependencies();
        boolean validateMocks = arguments.mockValidationEnabled();

        List<ClassMetadata> discovered = scanner.scan();
        MetadataTransformer transformer = new MetadataTransformer(discovered);
        TestDependencyDocumentationBuilder dependencyDocumentationBuilder = new TestDependencyDocumentationBuilder(
                new MethodDependencyAnalyzer(), transformer);
        List<ClassMetadata> selected = filterTargets(discovered, arguments);
        if (selected.isEmpty()) {
            System.out.println("No matching classes found. Nothing to do.");
            return;
        }

        GigachatClientConfig config = GigachatClientProperties.load();
        LLMClient llmClient = arguments.useTokenAuth()
                ? new GigachatLLMClient(config)
                : new GigaChatCertificateClient(config);

        List<MockRule> methodMockRules = List.of(
                new StaticVoidInvocationRule(),
                new NewObjectInvocationRule()
        );
        MethodMockValidationService methodMockValidationService = validateMocks
                ? new GigachatMethodMockValidationService(llmClient, this::defaultOptions)
                : null;
        MethodMockGenerator methodMockGenerator = new MethodMockGenerator(
                new TargetClassAnalyzer(),
                methodMockRules,
                new MockTemplateRepository(),
                methodMockValidationService
        );

        Duration requestDelay = arguments.requestDelay();

        List<GeneratedTestClass> generatedClasses = new ArrayList<>();
        Map<String, com.example.tests.generator.metadata.ClassMetadata> metadataByTestClass = new HashMap<>();
        long lastRequestAtNanos = -1L;
        for (ClassMetadata metadata : selected.stream().limit(arguments.limit()).collect(Collectors.toList())) {
            LOGGER.info(() -> "Обработка класса: " + metadata.getQualifiedName());
            com.example.tests.generator.metadata.ClassMetadata promptMetadata = transformer.transform(metadata);
            DependencyDocumentation dependencyDocumentation = dependencyDocumentationBuilder.buildDocumentation(
                    promptMetadata,
                    metadata,
                    ""
            );
            promptMetadata = promptMetadata.withDependencyDocumentation(
                    dependencyDocumentation.dependencyMethods(),
                    dependencyDocumentation.supportingTypes(),
                    focusDependencies ? dependencyDocumentation.targetMethods() : List.of(),
                    focusDependencies
            );

            List<MethodMockPlan> classMockPlans = loadMockPlans(metadata, methodMockGenerator, validateMocks);
            promptMetadata = promptMetadata.withMethodMockPlans(classMockPlans);
            logMockPlans(classMockPlans);
            String basePrompt = promptBuilder.buildPrompt(promptMetadata);
            String prompt = basePrompt;
            List<String> lastIssues = new ArrayList<>();
            String lastAttemptCode = null;
            boolean success = false;
            GeneratedTestClass lastGeneratedClass = null;
            pipeline.resetAudit();

            for (int attempt = 0; attempt <= arguments.maxRetries(); attempt++) {
                List<String> attemptIssues = new ArrayList<>();
                int attemptNumber = attempt + 1;
                String currentPrompt = prompt;
                LOGGER.info(() -> String.format(Locale.ENGLISH,
                        "Формирование запроса (%d/%d) для %s", attemptNumber,
                        arguments.maxRetries() + 1, metadata.getQualifiedName()));
                LOGGER.info(() -> "Запрос в Gigachat:\n" + currentPrompt);
                applyRequestDelay(requestDelay, lastRequestAtNanos);
                lastRequestAtNanos = System.nanoTime();
                String response = llmClient.sendPrompt(currentPrompt, defaultOptions());
                LOGGER.info(() -> "Ответ от Gigachat:\n" + response);
                pipeline.logGigachatExchange(currentPrompt, response);
                ValidationResult validationResult = responseValidator.validate(response, promptMetadata);
                Optional<String> sanitizedCode = validationResult.getSanitizedCode();
                Optional<GeneratedTestClass> parsedClass = Optional.empty();

                if (sanitizedCode.isPresent()) {
                    String code = sanitizedCode.get();
                    lastAttemptCode = code;
                    String loggableCode = code;
                    LOGGER.info(() -> "Полученный код:\n" + loggableCode);
                    parsedClass = codeParser.parse(code);
                    if (parsedClass.isEmpty()) {
                        attemptIssues.add("LLM response did not contain parsable Java code");
                    }
                }

                if (parsedClass.isPresent()) {
                    GeneratedTestClass generatedTestClass = testVerifier.verify(parsedClass.get(), promptMetadata);
                    parsedClass = Optional.of(generatedTestClass);
                    lastGeneratedClass = generatedTestClass;
                }

                if (!validationResult.isValid()) {
                    attemptIssues.addAll(validationResult.getErrors());
                }

                if (parsedClass.isPresent()) {
                    GeneratedTestClass generatedTestClass = parsedClass.get();
                    try {
                        TestGenerationPipeline.TestCompilationResult compilationResult = pipeline.verifyCompilation(generatedTestClass);
                        if (!compilationResult.successful()) {
                            List<String> compileErrors = new ArrayList<>(compilationResult.errors().isEmpty()
                                    ? List.of("Compilation failed but produced no diagnostics.")
                                    : compilationResult.errors());
                            compileErrors.forEach(error -> LOGGER.severe(() -> "Ошибка компиляции: " + error));
                            GeneratedTestClass fixedAfterCompile = testVerifier.verify(generatedTestClass, promptMetadata, compileErrors);
                            if (!fixedAfterCompile.getSourceCode().equals(generatedTestClass.getSourceCode())) {
                                GeneratedTestClass finalFixedAfterCompile = fixedAfterCompile;
                                LOGGER.info(() -> "Применение автоматических правок после ошибки компиляции");
                                TestGenerationPipeline.TestCompilationResult retryResult = pipeline.verifyCompilation(fixedAfterCompile);
                                if (retryResult.successful()) {
                                    compilationResult = retryResult;
                                    generatedTestClass = finalFixedAfterCompile;
                                    parsedClass = Optional.of(generatedTestClass);
                                    lastGeneratedClass = generatedTestClass;
                                    compileErrors.clear();
                                } else {
                                    List<String> retryErrors = retryResult.errors().isEmpty()
                                            ? List.of("Compilation retry failed without diagnostics.")
                                            : retryResult.errors();
                                    retryErrors.forEach(error -> LOGGER.severe(() -> "Ошибка компиляции (повтор): " + error));
                                    compileErrors.addAll(retryErrors);
                                }
                            }
                            if (!compileErrors.isEmpty()) {
                                attemptIssues.addAll(compileErrors);
                            }
                        }

                        if (compilationResult.successful() && validationResult.isValid()) {
                            generatedClasses.add(generatedTestClass);
                            metadataByTestClass.put(generatedTestClass.getFullyQualifiedName(), promptMetadata);
                            success = true;
                            break;
                        }
                    } catch (IOException ioException) {
                        String errorMessage = "Failed to persist generated test: " + ioException.getMessage();
                        LOGGER.severe(() -> errorMessage);
                        attemptIssues.add(errorMessage);
                    }
                }

                if (success) {
                    break;
                }

                lastIssues = attemptIssues.isEmpty() ? List.of("Validation reported issues but none were captured.") : new ArrayList<>(attemptIssues);
                prompt = promptBuilder.augmentWithFeedback(basePrompt, lastIssues, lastAttemptCode, promptMetadata);
            }

            if (!success) {
                System.err.println("Unable to generate tests for " + metadata.getQualifiedName() + ":");
                lastIssues.forEach(error -> System.err.println("  - " + error));
                if (lastGeneratedClass != null) {
                    String candidateName = lastGeneratedClass.getFullyQualifiedName();
                    boolean alreadyPresent = generatedClasses.stream()
                            .anyMatch(existing -> existing.getFullyQualifiedName().equals(candidateName));
                    metadataByTestClass.put(candidateName, promptMetadata);
                    if (!alreadyPresent) {
                        generatedClasses.add(lastGeneratedClass);
                    }
                }
            }

            if (!infoLogging) {
                String status = success ? "тест создан" : "тест не создан";
                System.out.printf(Locale.ROOT, "%s: %s%n", metadata.getQualifiedName(), status);
            } else {
                boolean finalSuccess = success;
                LOGGER.info(() -> String.format(Locale.ENGLISH, "%s: %s", metadata.getQualifiedName(),
                        finalSuccess ? "тест создан" : "тест не создан"));
            }
        }

        if (generatedClasses.isEmpty()) {
            System.err.println("No test classes were generated. Aborting.");
            return;
        }

        GenerationReport report = pipeline.process(generatedClasses);
        if (report.isSuccessful()) {
            System.out.println("Tests generated successfully.");
            report.getCoverageSummary().ifPresent(summary -> System.out.printf(Locale.ENGLISH,
                    "Coverage report: %s%n", summary.getReportPath()));
        } else {
            System.err.println("Generated tests but build failed.");
            report.getErrorReport().ifPresent(errorReport -> {
                System.err.println("Build tool: " + errorReport.getBuildTool());
                errorReport.getErrors().forEach(line -> System.err.println("  - " + line));
                errorReport.getErrors().forEach(line -> LOGGER.severe(() -> "Компиляция: " + line));
            });
        }

        if (!report.getClassesWithoutTests().isEmpty()) {
            System.out.println("Classes still lacking tests:");
            report.getClassesWithoutTests().forEach(className -> System.out.println("  - " + className));
        }

        if (!report.isSuccessful()) {
            Map<String, TestContextSnapshot> contexts = buildContextSnapshots(
                    projectRoot,
                    projectLayout,
                    generatedClasses,
                    metadataByTestClass,
                    transformer,
                    dependencyDocumentationBuilder
            );
            if (!contexts.isEmpty()) {
                PromptClient promptClient = llmClient::sendPrompt;
                FixIterationExecutionSettings executionSettings = new FixIterationExecutionSettings(
                        arguments.compileSuccessEnabled(),
                        arguments.executionSuccessEnabled());
                TestSignatureVerifier signatureVerifier = new SignatureComplianceVerifier();
                TestFixIterationCoordinator coordinator = new TestFixIterationCoordinator(
                        new GradleTestSuiteRunner(),
                        new TestFailureCollector(),
                        new GigachatFixGateway(promptClient, new GigachatFixRequestBuilder()),
                        new TestFixApplier(),
                        new GigachatResponseParser(),
                        new FixIterationLoopHandler(),
                        executionSettings,
                        signatureVerifier
                );
                TestRunRequest request = TestRunRequest.builder(projectRoot).build();
                coordinator.executeAndAttemptFix(request, contexts);
            }
        }
    }

    private Map<String, TestContextSnapshot> buildContextSnapshots(Path projectRoot,
                                                                   ProjectLayout projectLayout,
                                                                   List<GeneratedTestClass> generatedClasses,
                                                                   Map<String, com.example.tests.generator.metadata.ClassMetadata> metadataByTestClass,
                                                                   MetadataTransformer metadataTransformer,
                                                                   TestDependencyDocumentationBuilder dependencyDocumentationBuilder) {
        Map<String, TestContextSnapshot> contexts = new LinkedHashMap<>();
        for (GeneratedTestClass generated : generatedClasses) {
            Path sourceFile = resolveTestSourceFile(projectRoot, projectLayout, generated);
            if (!Files.exists(sourceFile)) {
                LOGGER.warning(() -> String.format(Locale.ENGLISH,
                        "Generated test file not found for %s at %s",
                        generated.getFullyQualifiedName(), sourceFile));
                continue;
            }
            String sourceCode;
            try {
                sourceCode = Files.readString(sourceFile);
            } catch (IOException e) {
                LOGGER.severe(() -> String.format(Locale.ENGLISH,
                        "Failed to read generated test %s: %s",
                        generated.getFullyQualifiedName(), e.getMessage()));
                continue;
            }

            com.example.tests.generator.metadata.ClassMetadata metadata = metadataByTestClass.get(generated.getFullyQualifiedName());
            com.example.tests.generator.model.ClassMetadata ownerMetadata = null;
            if (metadata != null) {
                ownerMetadata = metadataTransformer.findRawMetadata(metadata.getFullyQualifiedName()).orElse(null);
            }

            DependencyDocumentation dependencyDocumentation = dependencyDocumentationBuilder.buildDocumentation(
                    metadata,
                    ownerMetadata,
                    sourceCode
            );
            Map<String, List<String>> dependencyMethods = dependencyDocumentation.dependencyMethods();
            Map<String, List<String>> supportingTypes = dependencyDocumentation.supportingTypes();
            List<String> targetMethods = dependencyDocumentation.targetMethods();
            Map<String, List<String>> enumConstants = metadata == null
                    ? Map.of()
                    : extractEnumConstants(metadata);

            TestContextSnapshot snapshot = new TestContextSnapshot(
                    generated.getFullyQualifiedName(),
                    sourceFile,
                    sourceCode,
                    dependencyMethods,
                    supportingTypes,
                    enumConstants,
                    targetMethods
            );
            contexts.put(generated.getFullyQualifiedName(), snapshot);
        }
        return contexts;
    }

    private Path resolveTestSourceFile(Path projectRoot, ProjectLayout projectLayout, GeneratedTestClass generated) {
        Path testRoot = projectRoot.resolve(projectLayout.testSourceSet());
        String packageName = generated.getPackageName();
        if (packageName != null && !packageName.isBlank()) {
            testRoot = testRoot.resolve(packageName.replace('.', '/'));
        }
        return testRoot.resolve(generated.getClassName() + ".java");
    }

    private Map<String, List<String>> extractEnumConstants(com.example.tests.generator.metadata.ClassMetadata metadata) {
        Map<String, List<String>> enums = new LinkedHashMap<>();
        addEnumConstants(enums, metadata.getFullyQualifiedName(), metadata.isEnumType(), metadata.getEnumConstants());
        for (RelatedTypeMetadata type : metadata.getSupportingTypes()) {
            addEnumConstants(enums, type.getQualifiedName(), type.isEnumType(), type.getEnumConstants());
        }
        return enums;
    }

    private void addEnumConstants(Map<String, List<String>> target,
                                  String qualifiedName,
                                  boolean isEnum,
                                  List<String> constants) {
        if (!isEnum) {
            return;
        }
        if (constants.isEmpty()) {
            target.put(qualifiedName,
                    List.of("Enum constants not documented—ask for the declared values before using them."));
        } else {
            target.put(qualifiedName, List.copyOf(constants));
        }
    }

    private String formatSignature(String ownerSimpleName, com.example.tests.generator.metadata.MethodMetadata method) {
        String parameters = method.getParameters().stream()
                .map(com.example.tests.generator.metadata.ParameterMetadata::toString)
                .collect(Collectors.joining(", "));
        if (parameters.isBlank()) {
            parameters = "";
        }
        if (method.isConstructor()) {
            return ownerSimpleName + '(' + parameters + ')';
        }
        String qualifier = method.isStaticMethod() ? "static " : "";
        return qualifier + method.getReturnType() + ' ' + ownerSimpleName + '.' + method.getName() + '(' + parameters + ')';
    }

    private List<ClassMetadata> filterTargets(List<ClassMetadata> discovered, CliArguments arguments) {
        if (arguments.targetClasses().isEmpty()) {
            return discovered;
        }
        List<String> targets = arguments.targetClasses().stream()
                .map(String::trim)
                .filter(target -> !target.isEmpty())
                .collect(Collectors.toList());
        return discovered.stream()
                .filter(metadata -> matchesTarget(metadata, targets))
                .collect(Collectors.toList());
    }

    private boolean matchesTarget(ClassMetadata metadata, List<String> targets) {
        String qualifiedName = metadata.getQualifiedName();
        String simpleName = metadata.getClassName();
        for (String target : targets) {
            if (qualifiedName.equals(target)
                    || simpleName.equals(target)) {
                return true;
            }
            if (target.indexOf('.') >= 0 && qualifiedName.endsWith('.' + target)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> defaultOptions() {
        return Map.of(
                "temperature", 0.2,
                "top_p", 0.9
        );
    }

    private void applyRequestDelay(Duration delay, long lastRequestAtNanos) {
        if (delay.isZero() || lastRequestAtNanos < 0) {
            return;
        }
        long elapsed = System.nanoTime() - lastRequestAtNanos;
        long requiredDelay = delay.toNanos();
        long remaining = requiredDelay - elapsed;
        if (remaining <= 0) {
            return;
        }
        long millis = remaining / 1_000_000L;
        int nanos = (int) (remaining % 1_000_000L);
        try {
            Thread.sleep(millis, nanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Gigachat request delay", e);
        }
    }

    private void logMockPlans(List<MethodMockPlan> plans) {
        if (plans.isEmpty()) {
            return;
        }
        for (MethodMockPlan plan : plans) {
            if (plan.isEmpty() && plan.validationFeedback().isEmpty()) {
                continue;
            }
            LOGGER.info(() -> String.format(Locale.ENGLISH,
                    "Моки для %s#%s", plan.className(), plan.methodName()));
            for (MockSnippet snippet : plan.snippets()) {
                LOGGER.info(() -> "  Правило: " + snippet.ruleId());
                LOGGER.info(() -> "  Оригинал: " + snippet.originalCode());
                LOGGER.info(() -> "  Мок:\n" + snippet.mockCode());
            }
            plan.validationFeedback().ifPresent(feedback ->
                    LOGGER.info(() -> "Ответ Gigachat по мокам:\n" + feedback));
        }
    }

    private List<MethodMockPlan> loadMockPlans(ClassMetadata metadata,
                                               MethodMockGenerator generator,
                                               boolean validateMocks) {
        Path sourcePath = metadata.getSourcePath();
        if (sourcePath == null) {
            return List.of();
        }
        if (!Files.exists(sourcePath)) {
            LOGGER.warning(() -> String.format(Locale.ENGLISH,
                    "Source file for %s was not found at %s", metadata.getQualifiedName(), sourcePath));
            return List.of();
        }
        try {
            String source = Files.readString(sourcePath);
            return generator.generate(source, validateMocks);
        } catch (IOException e) {
            LOGGER.warning(() -> String.format(Locale.ENGLISH,
                    "Failed to read %s for mock analysis: %s", metadata.getQualifiedName(), e.getMessage()));
            return List.of();
        }
    }
}
