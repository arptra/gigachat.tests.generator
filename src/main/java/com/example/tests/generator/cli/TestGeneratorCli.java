package com.example.tests.generator.cli;

import com.example.agent.providers.GigaChatCertificateClient;
import com.example.agent.providers.GigachatLLMClient;
import com.example.agent.providers.LLMClient;
import com.example.tests.generator.metadata.MetadataTransformer;
import com.example.tests.generator.model.ClassMetadata;
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
import com.example.tests.generator.config.GigachatClientConfig;
import com.example.tests.generator.config.GigachatClientProperties;
import com.example.tests.generator.util.LoggingConfigurator;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        MetadataTransformer transformer = new MetadataTransformer();
        PromptBuilder promptBuilder = new PromptBuilder();
        ResponseValidator responseValidator = new ResponseValidator();
        TestCodeParser codeParser = new TestCodeParser();
        TestGenerationPipeline pipeline = new TestGenerationPipeline(projectRoot, projectLayout);

        boolean infoLogging = arguments.infoLogging();

        List<ClassMetadata> discovered = scanner.scan();
        List<ClassMetadata> selected = filterTargets(discovered, arguments);
        if (selected.isEmpty()) {
            System.out.println("No matching classes found. Nothing to do.");
            return;
        }

        GigachatClientConfig config = GigachatClientProperties.load();
        LLMClient llmClient = arguments.useTokenAuth()
                ? new GigachatLLMClient(config)
                : new GigaChatCertificateClient(config);

        Duration requestDelay = arguments.requestDelay();

        List<GeneratedTestClass> generatedClasses = new ArrayList<>();
        long lastRequestAtNanos = -1L;
        for (ClassMetadata metadata : selected.stream().limit(arguments.limit()).collect(Collectors.toList())) {
            LOGGER.info(() -> "Обработка класса: " + metadata.getQualifiedName());
            com.example.tests.generator.metadata.ClassMetadata promptMetadata = transformer.transform(metadata);
            String basePrompt = promptBuilder.buildPrompt(promptMetadata);
            String prompt = basePrompt;
            List<String> feedback = new ArrayList<>();
            boolean success = false;
            pipeline.resetAudit();

            for (int attempt = 0; attempt <= arguments.maxRetries(); attempt++) {
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
                ValidationResult validationResult = responseValidator.validate(response);
                validationResult.getSanitizedCode()
                        .ifPresent(code -> LOGGER.info(() -> "Полученный код:\n" + code));
                if (validationResult.isValid() && validationResult.getSanitizedCode().isPresent()) {
                    GeneratedTestClass parsedClass = validationResult.getSanitizedCode()
                            .flatMap(codeParser::parse)
                            .orElse(null);
                    if (parsedClass != null) {
                        generatedClasses.add(parsedClass);
                        success = true;
                        break;
                    }
                    feedback.add("LLM response did not contain parsable Java code");
                }
                feedback.addAll(validationResult.getErrors());
                prompt = promptBuilder.augmentWithFeedback(basePrompt, feedback);
            }

            if (!success) {
                System.err.println("Unable to generate tests for " + metadata.getQualifiedName() + ":");
                feedback.forEach(error -> System.err.println("  - " + error));
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
                    || simpleName.equals(target)
                    || qualifiedName.endsWith('.' + target)
                    || target.endsWith('.' + simpleName)) {
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
}
