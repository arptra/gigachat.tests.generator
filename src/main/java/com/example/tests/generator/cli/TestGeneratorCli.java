package com.example.tests.generator.cli;

import com.example.agent.providers.GigachatLLMClient;
import com.example.agent.providers.LLMClient;
import com.example.tests.generator.metadata.MetadataTransformer;
import com.example.tests.generator.model.ClassMetadata;
import com.example.tests.generator.pipeline.GeneratedTestClass;
import com.example.tests.generator.pipeline.GenerationReport;
import com.example.tests.generator.pipeline.TestGenerationPipeline;
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
            LoggingConfigurator.configure();
            CliArguments arguments = CliArguments.parse(args);
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
        LOGGER.info(() -> "Starting test generation for project " + projectRoot);
        ProjectScanner scanner = new ProjectScanner(projectRoot);
        MetadataTransformer transformer = new MetadataTransformer();
        PromptBuilder promptBuilder = new PromptBuilder();
        ResponseValidator responseValidator = new ResponseValidator();
        TestCodeParser codeParser = new TestCodeParser();
        TestGenerationPipeline pipeline = new TestGenerationPipeline(projectRoot);

        LOGGER.info("Scanning project for candidate classes");
        List<ClassMetadata> discovered = scanner.scan();
        LOGGER.info(() -> "Discovered " + discovered.size() + " classes in project");
        List<ClassMetadata> selected = filterTargets(discovered, arguments);
        LOGGER.info(() -> "Selected " + selected.size() + " classes matching filters");
        if (selected.isEmpty()) {
            System.out.println("No matching classes found. Nothing to do.");
            return;
        }

        LOGGER.info("Initializing Gigachat client");
        GigachatClientConfig config = GigachatClientProperties.load();
        LLMClient llmClient = new GigachatLLMClient(config);

        Duration requestDelay = arguments.requestDelay();
        if (!requestDelay.isZero()) {
            LOGGER.info(() -> "Applying delay of " + requestDelay.toSeconds()
                    + " seconds between Gigachat requests");
        }

        List<GeneratedTestClass> generatedClasses = new ArrayList<>();
        long lastRequestAtNanos = -1L;
        for (ClassMetadata metadata : selected.stream().limit(arguments.limit()).collect(Collectors.toList())) {
            LOGGER.info(() -> "Generating tests for " + metadata.getQualifiedName());
            com.example.tests.generator.metadata.ClassMetadata promptMetadata = transformer.transform(metadata);
            String basePrompt = promptBuilder.buildPrompt(promptMetadata);
            String prompt = basePrompt;
            List<String> feedback = new ArrayList<>();
            boolean success = false;

            for (int attempt = 0; attempt <= arguments.maxRetries(); attempt++) {
                int attemptNumber = attempt + 1;
                LOGGER.info(() -> String.format(Locale.ENGLISH,
                        "Requesting Gigachat response (attempt %d/%d) for %s", attemptNumber,
                        arguments.maxRetries() + 1, metadata.getQualifiedName()));
                String currentPrompt = prompt;
                LOGGER.fine(() -> "Gigachat request for " + metadata.getQualifiedName()
                        + ":\n" + currentPrompt);
                applyRequestDelay(requestDelay, lastRequestAtNanos);
                lastRequestAtNanos = System.nanoTime();
                String response = llmClient.sendPrompt(currentPrompt, defaultOptions());
                LOGGER.fine(() -> "Received response from Gigachat for " + metadata.getQualifiedName());
                LOGGER.fine(() -> "Gigachat response for " + metadata.getQualifiedName()
                        + ":\n" + response);
                pipeline.logGigachatExchange(currentPrompt, response);
                ValidationResult validationResult = responseValidator.validate(response);
                LOGGER.info(() -> "Validation result for " + metadata.getQualifiedName() + ": "
                        + (validationResult.isValid() ? "valid" : "invalid"));
                if (validationResult.isValid() && validationResult.getSanitizedCode().isPresent()) {
                    boolean parsed = validationResult.getSanitizedCode()
                            .flatMap(codeParser::parse)
                            .map(generatedClasses::add)
                            .orElse(false);
                    if (parsed) {
                        LOGGER.info(() -> "Successfully parsed generated tests for " + metadata.getQualifiedName());
                        success = true;
                        break;
                    }
                    feedback.add("LLM response did not contain parsable Java code");
                    LOGGER.warning(() -> "Failed to parse generated code for " + metadata.getQualifiedName());
                }
                feedback.addAll(validationResult.getErrors());
                prompt = promptBuilder.augmentWithFeedback(basePrompt, feedback);
                LOGGER.info(() -> "Augmenting prompt with feedback for " + metadata.getQualifiedName());
            }

            if (!success) {
                System.err.println("Unable to generate tests for " + metadata.getQualifiedName() + ":");
                feedback.forEach(error -> System.err.println("  - " + error));
                LOGGER.warning(() -> "Failed to generate tests for " + metadata.getQualifiedName());
            }
        }

        if (generatedClasses.isEmpty()) {
            System.err.println("No test classes were generated. Aborting.");
            LOGGER.warning("No test classes generated");
            return;
        }

        LOGGER.info("Persisting generated tests and validating build");
        GenerationReport report = pipeline.process(generatedClasses);
        if (report.isSuccessful()) {
            System.out.println("Tests generated successfully.");
            report.getCoverageSummary().ifPresent(summary -> System.out.printf(Locale.ENGLISH,
                    "Coverage report: %s%n", summary.getReportPath()));
            LOGGER.info("Generation pipeline completed successfully");
        } else {
            System.err.println("Generated tests but build failed.");
            report.getErrorReport().ifPresent(errorReport -> {
                System.err.println("Build tool: " + errorReport.getBuildTool());
                errorReport.getErrors().forEach(line -> System.err.println("  - " + line));
            });
            LOGGER.warning("Generated tests but build failed");
        }

        if (!report.getClassesWithoutTests().isEmpty()) {
            System.out.println("Classes still lacking tests:");
            report.getClassesWithoutTests().forEach(className -> System.out.println("  - " + className));
            LOGGER.info(() -> "Classes without tests remaining: " + report.getClassesWithoutTests().size());
        }
    }

    private List<ClassMetadata> filterTargets(List<ClassMetadata> discovered, CliArguments arguments) {
        if (arguments.targetClasses().isEmpty()) {
            return discovered;
        }
        List<String> targets = arguments.targetClasses();
        return discovered.stream()
                .filter(metadata -> targets.contains(metadata.getQualifiedName()))
                .collect(Collectors.toList());
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
