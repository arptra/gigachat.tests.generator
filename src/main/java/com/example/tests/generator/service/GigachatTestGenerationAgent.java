package com.example.tests.generator.service;

import com.example.agent.providers.LLMClient;
import com.example.tests.generator.model.ClassMetadata;
import com.example.tests.generator.model.TestGenerationResult;
import com.example.tests.generator.prompts.PromptFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.logging.Logger;

/**
 * Coordinates the prompts sent to Gigachat to convert metadata into test suites.
 */
public class GigachatTestGenerationAgent {

    private final LLMClient llmClient;
    private final PromptFactory promptFactory;
    private static final Logger LOGGER = Logger.getLogger(GigachatTestGenerationAgent.class.getName());

    public GigachatTestGenerationAgent(LLMClient llmClient, PromptFactory promptFactory) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.promptFactory = Objects.requireNonNull(promptFactory, "promptFactory");
    }

    public TestGenerationResult generateTests(ClassMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");

        // Step 1: ask for analysis
        LOGGER.info(() -> "Requesting analysis for " + metadata.getQualifiedName());
        String analysisPrompt = promptFactory.buildAnalysisPrompt(metadata);
        String analysis = llmClient.sendPrompt(analysisPrompt, defaultOptions());
        LOGGER.info(() -> "Received analysis for " + metadata.getQualifiedName());

        // Step 2: ask for draft tests using streaming to provide immediate feedback
        LOGGER.info(() -> "Requesting draft tests for " + metadata.getQualifiedName());
        String draftPrompt = promptFactory.buildTestDraftPrompt(metadata, analysis);
        List<String> streamed = llmClient.streamResponses(draftPrompt, streamOptions())
                .collect(Collectors.toList());
        String draft = String.join("", streamed);
        LOGGER.info(() -> "Draft test generation completed for " + metadata.getQualifiedName());

        // Step 3: optionally refine
        LOGGER.info(() -> "Requesting refinement for " + metadata.getQualifiedName());
        String refinementPrompt = promptFactory.buildRefinementPrompt(metadata, draft);
        String refinement = llmClient.sendPrompt(refinementPrompt, defaultOptions());
        LOGGER.info(() -> "Refinement completed for " + metadata.getQualifiedName());

        List<String> chunks = new ArrayList<>(streamed);
        chunks.add("\nRefinement summary:\n" + refinement);
        return new TestGenerationResult(analysis, draft, chunks);
    }

    private Map<String, Object> defaultOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put("temperature", 0.2);
        options.put("top_p", 0.9);
        return options;
    }

    private Map<String, Object> streamOptions() {
        Map<String, Object> options = defaultOptions();
        options.put("stream", true);
        return options;
    }
}
