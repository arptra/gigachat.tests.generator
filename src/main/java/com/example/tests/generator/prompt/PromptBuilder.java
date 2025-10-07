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

    private static final String FEEDBACK_TEMPLATE = """
Previous attempt issues:
%s
""";

    private static final String FEEDBACK_ACTIONS = """
Adjust your next response accordingly:
- If any requirement is unclear or references unavailable domain objects, ask a concise clarifying question before the checklist instead of guessing.
- Provide a short checklist describing how you will resolve each issue above before emitting code.
- If a type, method, or helper object is missing, either remove that dependency or declare a minimal helper inside the test class before using it. Never invent new production code.
- After the checklist, output the revised test class inside a ```java``` block and do not append any extra commentary after the block.
- Double-check imports and API usage so the code compiles without relying on undefined classes.
""";

    public String buildPrompt(ClassMetadata metadata) {
        StringBuilder builder = new StringBuilder();
        builder.append(PromptTemplates.renderClassDescription(metadata));
        builder.append(PromptTemplates.renderDependencies(metadata.getDependencies()));
        builder.append(PromptTemplates.renderSupportingTypes(metadata));
        builder.append(PromptTemplates.renderEnumConstants(metadata));
        builder.append(PromptTemplates.renderCoverageSection(metadata));
        builder.append(PromptTemplates.renderMockingRestrictions(metadata));
        builder.append(PromptTemplates.renderExampleScenarios(metadata));
        builder.append(PromptTemplates.renderTestRequirements(metadata));
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
        return basePrompt + System.lineSeparator()
                + String.format(Locale.ENGLISH, FEEDBACK_TEMPLATE, bulletList)
                + FEEDBACK_ACTIONS
                + System.lineSeparator();
    }
}
