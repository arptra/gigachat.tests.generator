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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Entry point for the Gigachat powered unit test generator.
 */
public final class TestGeneratorCli {

    public static void main(String[] args) {
        try {
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
        ProjectScanner scanner = new ProjectScanner(projectRoot);
        MetadataTransformer transformer = new MetadataTransformer();
        PromptBuilder promptBuilder = new PromptBuilder();
        ResponseValidator responseValidator = new ResponseValidator();
        TestCodeParser codeParser = new TestCodeParser();
        TestGenerationPipeline pipeline = new TestGenerationPipeline(projectRoot);

        List<ClassMetadata> discovered = scanner.scan();
        List<ClassMetadata> selected = filterTargets(discovered, arguments);
        if (selected.isEmpty()) {
            if (!arguments.targetClasses().isEmpty()) {
                System.out.printf(Locale.ENGLISH,
                        "No matching classes found for %s.%n",
                        arguments.targetClasses());
                if (discovered.isEmpty()) {
                    System.out.printf(Locale.ENGLISH,
                            "No Java sources were found under %s. Ensure the project contains compilable classes.%n",
                            projectRoot);
                } else {
                    System.out.println("Discovered classes in the project:");
                    List<String> discoveredNames = discovered.stream()
                            .map(ClassMetadata::getQualifiedName)
                            .sorted()
                            .collect(Collectors.toList());
                    int preview = Math.min(discoveredNames.size(), 10);
                    for (int i = 0; i < preview; i++) {
                        System.out.println("  - " + discoveredNames.get(i));
                    }
                    if (discoveredNames.size() > preview) {
                        System.out.printf(Locale.ENGLISH,
                                "  ... and %d more. Omit --class to process every discovered class.%n",
                                discoveredNames.size() - preview);
                    }
                }
            } else {
                System.out.printf(Locale.ENGLISH,
                        "No Java sources were found under %s. Nothing to do.%n",
                        projectRoot);
            }
            return;
        }

        GigachatClientConfig config = GigachatClientProperties.load();
        LLMClient llmClient = new GigachatLLMClient(config);

        List<GeneratedTestClass> generatedClasses = new ArrayList<>();
        for (ClassMetadata metadata : selected.stream().limit(arguments.limit()).collect(Collectors.toList())) {
            com.example.tests.generator.metadata.ClassMetadata promptMetadata = transformer.transform(metadata);
            String basePrompt = promptBuilder.buildPrompt(promptMetadata);
            String prompt = basePrompt;
            List<String> feedback = new ArrayList<>();
            boolean success = false;

            for (int attempt = 0; attempt <= arguments.maxRetries(); attempt++) {
                String response = llmClient.sendPrompt(prompt, defaultOptions());
                pipeline.logGigachatExchange(prompt, response);
                ValidationResult validationResult = responseValidator.validate(response);
                if (validationResult.isValid() && validationResult.getSanitizedCode().isPresent()) {
                    boolean parsed = validationResult.getSanitizedCode()
                            .flatMap(codeParser::parse)
                            .map(generatedClasses::add)
                            .orElse(false);
                    if (parsed) {
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
}
