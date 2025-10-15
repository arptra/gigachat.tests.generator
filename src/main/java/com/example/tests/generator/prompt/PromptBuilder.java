package com.example.tests.generator.prompt;

import com.example.tests.generator.metadata.ClassMetadata;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Produces complete prompts for the LLM based on class metadata and optional
 * feedback from previous validation attempts.
 */
public class PromptBuilder {

    private static final String FEEDBACK_TEMPLATE =
            "Previous attempt issues:%n%s";
    private static final String CODE_CONTEXT_TEMPLATE =
            "Latest attempt under review:%n```java%n%s%n```";
    private static final String API_REFERENCE_HEADER =
            "API reference reminders:";

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
        return augmentWithFeedback(basePrompt, feedback, null, null);
    }

    public String augmentWithFeedback(String basePrompt,
                                      List<String> feedback,
                                      String previousAttemptCode,
                                      ClassMetadata metadata) {
        boolean hasFeedback = feedback != null && !feedback.isEmpty();
        boolean hasCode = previousAttemptCode != null && !previousAttemptCode.isBlank();
        boolean hasMetadata = metadata != null;

        if (!hasFeedback && !hasCode && !hasMetadata) {
            return basePrompt;
        }

        StringBuilder builder = new StringBuilder(basePrompt).append(System.lineSeparator());

        if (hasFeedback) {
            String bulletList = feedback.stream()
                    .distinct()
                    .map(issue -> "  - " + issue)
                    .collect(Collectors.joining(System.lineSeparator()));
            builder.append(String.format(Locale.ENGLISH, FEEDBACK_TEMPLATE, bulletList))
                    .append(System.lineSeparator());
        }

        if (hasCode) {
            builder.append(String.format(Locale.ENGLISH, CODE_CONTEXT_TEMPLATE,
                    previousAttemptCode.trim()))
                    .append(System.lineSeparator());
        }

        if (hasMetadata) {
            builder.append(API_REFERENCE_HEADER).append(System.lineSeparator())
                    .append(renderApiReference(metadata))
                    .append(System.lineSeparator());
        }

        return builder.toString();
    }

    private String renderApiReference(ClassMetadata metadata) {
        StringBuilder builder = new StringBuilder();
        if (!metadata.getMethods().isEmpty()) {
            metadata.getMethods().forEach(method -> builder.append("  - ")
                    .append(formatSignature(metadata.getClassName(), method))
                    .append(System.lineSeparator()));
        }

        Map<String, List<String>> dependencyMethods = metadata.getDependencyMethods();
        if (!dependencyMethods.isEmpty()) {
            if (builder.length() > 0) {
                builder.append(System.lineSeparator());
            }
            builder.append("  Documented dependency methods:").append(System.lineSeparator());
            dependencyMethods.forEach((type, members) -> {
                builder.append("    - ").append(type).append(System.lineSeparator());
                members.forEach(member -> builder.append("      - ").append(member).append(System.lineSeparator()));
                builder.append(System.lineSeparator());
            });
        }

        Map<String, List<String>> supportingTypeMembers = metadata.getSupportingTypeMembers();
        if (!supportingTypeMembers.isEmpty()) {
            if (builder.length() > 0) {
                builder.append(System.lineSeparator());
            }
            builder.append("  Supporting types:").append(System.lineSeparator());
            supportingTypeMembers.forEach((type, members) -> {
                builder.append("    - ").append(type).append(System.lineSeparator());
                members.forEach(member -> builder.append("      - ").append(member).append(System.lineSeparator()));
                builder.append(System.lineSeparator());
            });
        } else if (!metadata.getSupportingTypes().isEmpty()) {
            if (builder.length() > 0) {
                builder.append(System.lineSeparator());
            }
            builder.append("  Supporting types:").append(System.lineSeparator());
            metadata.getSupportingTypes().forEach(type -> {
                builder.append("    - ").append(type.getQualifiedName()).append(System.lineSeparator());
                type.getMethods().forEach(method -> builder.append("      - ")
                        .append(formatSignature(type.getClassName(), method))
                        .append(System.lineSeparator()));
                if (type.isEnumType()) {
                    if (type.getEnumConstants().isEmpty()) {
                        builder.append("      - Enum constants not documented—ask for the declared values before using them.")
                                .append(System.lineSeparator());
                    } else {
                        builder.append("      - Enum constants: ")
                                .append(String.join(", ", type.getEnumConstants()))
                                .append(System.lineSeparator());
                    }
                }
                builder.append(System.lineSeparator());
            });
        }
        String api = builder.toString();
        if (api.isBlank()) {
            return "  (no public API metadata available)";
        }
        return api.stripTrailing();
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
}
