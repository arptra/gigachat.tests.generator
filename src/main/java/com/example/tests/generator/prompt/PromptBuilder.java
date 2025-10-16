package com.example.tests.generator.prompt;

import com.example.tests.generator.metadata.ClassMetadata;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
        builder.append(PromptTemplates.renderDependencies(metadata));
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
        if (metadata.isDependencyFocusEnabled() && !metadata.getTargetMethods().isEmpty()) {
            metadata.getTargetMethods().forEach(method -> builder.append("  - ")
                    .append(method)
                    .append(System.lineSeparator()));
        } else if (!metadata.getMethods().isEmpty()) {
            metadata.getMethods().forEach(method -> builder.append("  - ")
                    .append(formatSignature(metadata.getClassName(), method))
                    .append(System.lineSeparator()));
        }

        Map<String, List<String>> mergedDependencyMembers = new LinkedHashMap<>();
        metadata.getDependencyMethods().forEach((type, members) ->
                mergedDependencyMembers.put(type, new ArrayList<>(members)));
        metadata.getSupportingTypeMembers().forEach((type, members) -> {
            List<String> mergedMembers = mergedDependencyMembers.computeIfAbsent(type,
                    key -> new ArrayList<>());
            Set<String> unique = new LinkedHashSet<>(mergedMembers);
            for (String member : members) {
                if (unique.add(member)) {
                    mergedMembers.add(member);
                }
            }
        });

        if (!mergedDependencyMembers.isEmpty()) {
            if (builder.length() > 0) {
                builder.append(System.lineSeparator());
            }
            builder.append("  Documented dependency methods:").append(System.lineSeparator());
            mergedDependencyMembers.forEach((type, members) -> {
                builder.append("    - ").append(type).append(System.lineSeparator());
                members.forEach(member -> builder.append("      - ").append(member).append(System.lineSeparator()));
                builder.append(System.lineSeparator());
            });
        }

        Map<String, List<String>> supportingTypeMembers = new LinkedHashMap<>();
        metadata.getSupportingTypeMembers().forEach((type, members) -> {
            List<String> documentedMembers = mergedDependencyMembers.get(type);
            Set<String> documented = documentedMembers == null
                    ? Set.of()
                    : new LinkedHashSet<>(documentedMembers);
            List<String> uniqueMembers = new ArrayList<>();
            for (String member : members) {
                if (!documented.contains(member)) {
                    uniqueMembers.add(member);
                }
            }
            if (!uniqueMembers.isEmpty()) {
                supportingTypeMembers.put(type, uniqueMembers);
            }
        });

        if (!metadata.getSupportingTypes().isEmpty()) {
            metadata.getSupportingTypes().forEach(type -> {
                String qualifiedName = type.getQualifiedName();
                List<String> documentedMembers = mergedDependencyMembers.get(qualifiedName);
                Set<String> documented = documentedMembers == null
                        ? new LinkedHashSet<>()
                        : new LinkedHashSet<>(documentedMembers);
                List<String> additions = new ArrayList<>();
                type.getMethods().forEach(method -> {
                    String signature = formatSignature(type.getClassName(), method);
                    if (!documented.contains(signature)) {
                        additions.add(signature);
                    }
                });
                if (type.isEnumType()) {
                    String enumLine;
                    if (type.getEnumConstants().isEmpty()) {
                        enumLine = "Enum constants not documented—ask for the declared values before using them.";
                    } else {
                        enumLine = "Enum constants: " + String.join(", ", type.getEnumConstants());
                    }
                    if (!additions.contains(enumLine) && !documented.contains(enumLine)) {
                        additions.add(enumLine);
                    }
                }
                if (!additions.isEmpty()) {
                    supportingTypeMembers.merge(qualifiedName, additions, (existing, extra) -> {
                        List<String> merged = new ArrayList<>(existing);
                        Set<String> seen = new LinkedHashSet<>(existing);
                        for (String entry : extra) {
                            if (seen.add(entry)) {
                                merged.add(entry);
                            }
                        }
                        return merged;
                    });
                }
            });
        }

        if (!supportingTypeMembers.isEmpty()) {
            if (builder.length() > 0) {
                builder.append(System.lineSeparator());
            }
            builder.append("  Supporting types:").append(System.lineSeparator());
            Set<String> documentedTypes = new HashSet<>(supportingTypeMembers.keySet());
            supportingTypeMembers.forEach((type, members) -> {
                builder.append("    - ").append(type).append(System.lineSeparator());
                members.forEach(member -> builder.append("      - ").append(member).append(System.lineSeparator()));
                builder.append(System.lineSeparator());
            });
            metadata.getSupportingTypes().forEach(type -> {
                if (documentedTypes.contains(type.getQualifiedName())) {
                    return;
                }
                List<String> entries = new ArrayList<>();
                type.getMethods().forEach(method -> entries.add(formatSignature(type.getClassName(), method)));
                if (type.isEnumType()) {
                    if (type.getEnumConstants().isEmpty()) {
                        entries.add("Enum constants not documented—ask for the declared values before using them.");
                    } else {
                        entries.add("Enum constants: " + String.join(", ", type.getEnumConstants()));
                    }
                }
                if (entries.isEmpty()) {
                    return;
                }
                builder.append("    - ").append(type.getQualifiedName()).append(System.lineSeparator());
                entries.forEach(entry -> builder.append("      - ").append(entry).append(System.lineSeparator()));
                builder.append(System.lineSeparator());
            });
        } else if (!metadata.getSupportingTypes().isEmpty()) {
            List<com.example.tests.generator.metadata.RelatedTypeMetadata> fallbackTypes = metadata.getSupportingTypes()
                    .stream()
                    .filter(type -> !mergedDependencyMembers.containsKey(type.getQualifiedName()))
                    .collect(Collectors.toList());
            if (!fallbackTypes.isEmpty()) {
                if (builder.length() > 0) {
                    builder.append(System.lineSeparator());
                }
                builder.append("  Supporting types:").append(System.lineSeparator());
                fallbackTypes.forEach(type -> {
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
