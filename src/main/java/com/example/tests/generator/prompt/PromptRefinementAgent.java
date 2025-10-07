package com.example.tests.generator.prompt;

import com.example.agent.providers.LLMClient;
import com.example.tests.generator.metadata.ClassMetadata;
import com.example.tests.generator.metadata.RelatedTypeMetadata;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Delegates to a lightweight helper agent that can reshape the prompt delivered to the
 * main coding model. The refinement agent looks at the collected validation feedback and,
 * when repeated compilation problems are detected, asks the LLM to craft a focused preface
 * that nudges the coding agent to request missing data (enum constants, helper types, etc.)
 * before emitting more Java code.
 */
public final class PromptRefinementAgent {

    private final LLMClient llmClient;

    public PromptRefinementAgent(LLMClient llmClient) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
    }

    /**
     * Uses the helper LLM to generate an instructional preface based on the accumulated
     * validation feedback. The returned text can be prepended to the next coding prompt so
     * the primary agent explicitly asks for missing information and adjusts its plan.
     *
     * @param metadata metadata for the class under test
     * @param feedback validation feedback collected so far
     * @return optional preface text; empty if refinement is unnecessary or the helper returned
     * an unusable response
     */
    public Optional<String> generatePreface(ClassMetadata metadata, List<String> feedback) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(feedback, "feedback");
        if (feedback.isEmpty()) {
            return Optional.empty();
        }

        if (feedback.stream().noneMatch(PromptRefinementAgent::shouldEscalate)) {
            return Optional.empty();
        }

        String helperPrompt = buildHelperPrompt(metadata, feedback);
        String response = llmClient.sendPrompt(helperPrompt, defaultOptions());
        if (response == null) {
            return Optional.empty();
        }
        String trimmed = response.strip();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(trimmed);
    }

    private static boolean shouldEscalate(String error) {
        if (error == null || error.isBlank()) {
            return false;
        }
        String lower = error.toLowerCase(Locale.ENGLISH);
        return lower.contains("enum constant")
                || lower.contains("cannot be converted")
                || lower.contains("unresolved")
                || lower.contains("missing required imports")
                || lower.contains("no public test class")
                || lower.contains("compilation error");
    }

    private String buildHelperPrompt(ClassMetadata metadata, List<String> feedback) {
        StringJoiner joiner = new StringJoiner(System.lineSeparator());
        feedback.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(issue -> !issue.isEmpty())
                .distinct()
                .forEach(issue -> joiner.add("- " + issue));
        String issues = joiner.toString();

        List<String> knownEnumConstants = metadata.getSupportingTypes().stream()
                .filter(RelatedTypeMetadata::isEnumType)
                .flatMap(type -> type.getEnumConstants().stream())
                .toList();

        StringBuilder context = new StringBuilder();
        context.append("You are assisting another coding agent that writes tests for ")
                .append(metadata.getPackageName()).append('.')
                .append(metadata.getClassName()).append('.')
                .append(System.lineSeparator())
                .append("The agent keeps failing validation because of:")
                .append(System.lineSeparator())
                .append(issues.isEmpty() ? "- (no issues summarised)" : issues)
                .append(System.lineSeparator())
                .append("Craft a short preface (no markdown fences) that will be prepended to the next")
                .append(" coding prompt. The preface must:"
                        + System.lineSeparator()
                        + "1. Tell the agent to ask targeted clarifying questions to resolve the issues above before emitting code." + System.lineSeparator()
                        + "2. Remind them to wait for the answers or explicitly acknowledge missing data if it cannot be provided." + System.lineSeparator()
                        + "3. Highlight the concrete fixes required so the next answer compiles.");

        if (!knownEnumConstants.isEmpty()) {
            context.append(System.lineSeparator())
                    .append("If enum constants are required, you may supply the known values: ")
                    .append(String.join(", ", knownEnumConstants)).append('.');
        }

        context.append(System.lineSeparator())
                .append("Respond only with the preface text. Do not include Java code or wrap the response in backticks.");

        return context.toString();
    }

    private Map<String, Object> defaultOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put("temperature", 0.1);
        options.put("top_p", 0.9);
        return options;
    }
}

