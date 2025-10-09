package com.example.tests.generator.core;

import com.example.tests.generator.metadata.ClassMetadata;
import com.example.tests.generator.prompt.PromptBuilder;
import com.example.tests.generator.validate.ResponseValidator;
import com.example.tests.generator.validate.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Coordinates prompt building, validation and retry logic for generating unit tests.
 */
public class TestGenerationAgent {

    private final PromptBuilder promptBuilder;
    private final ResponseValidator responseValidator;
    private final TestResponseProvider responseProvider;
    private final int maxRetries;

    public TestGenerationAgent(PromptBuilder promptBuilder,
                               ResponseValidator responseValidator,
                               TestResponseProvider responseProvider,
                               int maxRetries) {
        this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder");
        this.responseValidator = Objects.requireNonNull(responseValidator, "responseValidator");
        this.responseProvider = Objects.requireNonNull(responseProvider, "responseProvider");
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0");
        }
        this.maxRetries = maxRetries;
    }

    public String generateTests(ClassMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        String basePrompt = promptBuilder.buildPrompt(metadata);
        String prompt = basePrompt;
        List<String> accumulatedErrors = new ArrayList<>();
        String lastAttemptCode = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            String response = responseProvider.generate(prompt);
            ValidationResult result = responseValidator.validate(response, metadata);
            Optional<String> sanitizedCode = result.getSanitizedCode();
            if (sanitizedCode.isPresent()) {
                lastAttemptCode = sanitizedCode.get();
            }
            if (result.isValid()) {
                return response;
            }
            accumulatedErrors.addAll(result.getErrors());
            prompt = promptBuilder.augmentWithFeedback(basePrompt, accumulatedErrors, lastAttemptCode, metadata);
        }

        throw new IllegalStateException("Unable to produce a valid test class. Last errors: " + accumulatedErrors);
    }
}
