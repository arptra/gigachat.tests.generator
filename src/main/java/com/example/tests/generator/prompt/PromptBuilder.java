package com.example.tests.generator.prompt;

import com.example.tests.generator.metadata.ClassMetadata;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Produces complete prompts for the LLM based on class metadata and optional
 * feedback from previous validation attempts.
 */
public class PromptBuilder {

    private static final String FEEDBACK_TEMPLATE =
            "Previous attempt issues:%n%s";

    public String buildPrompt(ClassMetadata metadata) {
        StringBuilder builder = new StringBuilder();
        builder.append(PromptTemplates.renderClassDescription(metadata));
        builder.append(PromptTemplates.renderDependencies(metadata.getDependencies()));
        builder.append(PromptTemplates.renderSupportingTypes(metadata));
        builder.append(PromptTemplates.renderEnumConstants(metadata));
        builder.append(PromptTemplates.renderCoverageSection(metadata));
        builder.append(PromptTemplates.renderMockingRestrictions(metadata));
        builder.append(PromptTemplates.renderExampleScenarios(metadata));
        builder.append(PromptTemplates.renderTestRequirements());
        return builder.toString();
    }

    public String augmentWithFeedback(String basePrompt, List<String> feedback) {
        if (feedback == null || feedback.isEmpty()) {
            return basePrompt;
        }
        String bulletList = feedback.stream()
                .distinct()
                .map(issue -> "  - " + issue)
                .collect(Collectors.joining(System.lineSeparator()));
        return basePrompt + System.lineSeparator() +
                String.format(Locale.ENGLISH, FEEDBACK_TEMPLATE, bulletList) + System.lineSeparator();
    }
}
