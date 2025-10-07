package com.example.tests.generator.prompt;

import com.example.tests.generator.metadata.ClassMetadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    private static final String ADAPTIVE_GUIDANCE_TEMPLATE = """
Follow-up strategy:
%s
""";

    private static final Pattern ENUM_CONSTANT_PATTERN = Pattern.compile("([A-Za-z0-9_.]+) does not declare enum constant");
    private static final Pattern TYPE_UNRESOLVED_PATTERN = Pattern.compile("Type ([A-Za-z0-9_.]+) is unresolved");
    private static final Pattern PACKAGE_UNAVAILABLE_PATTERN = Pattern.compile("Package ([A-Za-z0-9_.]+) is not available");

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
        Map<String, Long> counts = feedback.stream()
                .filter(issue -> issue != null && !issue.isBlank())
                .collect(Collectors.groupingBy(issue -> issue,
                        LinkedHashMap::new,
                        Collectors.counting()));
        if (counts.isEmpty()) {
            return basePrompt;
        }
        String bulletList = counts.keySet().stream()
                .map(issue -> "  - " + issue)
                .collect(Collectors.joining(System.lineSeparator()));
        List<String> adaptiveGuidance = buildAdaptiveGuidance(counts);
        StringBuilder builder = new StringBuilder(basePrompt)
                .append(System.lineSeparator())
                .append(String.format(Locale.ENGLISH, FEEDBACK_TEMPLATE, bulletList));
        String adaptiveBody;
        if (adaptiveGuidance.isEmpty()) {
            adaptiveBody = "  - Review the issues above and plan concrete fixes before providing the next code block.";
        } else {
            adaptiveBody = adaptiveGuidance.stream()
                    .map(item -> "  - " + item)
                    .collect(Collectors.joining(System.lineSeparator()));
        }
        builder.append(String.format(Locale.ENGLISH, ADAPTIVE_GUIDANCE_TEMPLATE, adaptiveBody));
        builder.append(FEEDBACK_ACTIONS).append(System.lineSeparator());
        return builder.toString();
    }

    private List<String> buildAdaptiveGuidance(Map<String, Long> counts) {
        LinkedHashSet<String> directives = new LinkedHashSet<>();
        Collection<String> errors = counts.keySet();

        if (counts.values().stream().anyMatch(count -> count > 1)) {
            directives.add("You are repeating the same failures—ask for the missing details or prompt guidance before sending more code so the next attempt compiles.");
        }

        Set<String> enumTypes = errors.stream()
                .map(PromptBuilder::extractEnumOwner)
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!enumTypes.isEmpty()) {
            directives.add(String.format(Locale.ENGLISH,
                    "Request the declared enum constants for %s instead of inventing placeholder values.",
                    String.join(", ", enumTypes)));
        } else if (errors.stream().anyMatch(error -> error.toLowerCase(Locale.ENGLISH).contains("enum constant"))) {
            directives.add("Ask for the enum constants before using them; do not fabricate new enums.");
        }

        Set<String> unresolvedTypes = errors.stream()
                .map(PromptBuilder::extractUnresolvedType)
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!unresolvedTypes.isEmpty()) {
            directives.add(String.format(Locale.ENGLISH,
                    "Clarify or request definitions for unresolved types such as %s before proceeding.",
                    String.join(", ", unresolvedTypes)));
        }

        Set<String> unavailablePackages = errors.stream()
                .map(PromptBuilder::extractUnavailablePackage)
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!unavailablePackages.isEmpty()) {
            directives.add(String.format(Locale.ENGLISH,
                    "Remove or replace references to unavailable packages (%s) unless the user confirms they exist.",
                    String.join(", ", unavailablePackages)));
        }

        if (errors.stream().anyMatch(error -> error.startsWith("Method "))) {
            directives.add("Stick to the documented public API—replace unresolved method calls with the listed accessors (for example, use getters like getCategory(), getQuantity(), getUnitPrice(), and getLineTotal()).");
        }

        if (errors.stream().anyMatch(error -> error.contains("Direct field access"))) {
            directives.add("Do not access record or enum fields directly; rely on the documented accessor methods instead.");
        }

        if (errors.stream().anyMatch(error -> error.contains("Do not instantiate"))) {
            directives.add("Avoid instantiating collaborators that should be mocked—use Mockito mocks or ask for the correct construction pattern.");
        }

        if (errors.stream().anyMatch(error -> error.contains("MockitoAnnotations"))) {
            directives.add("Drop manual MockitoAnnotations usage and rely on @ExtendWith(MockitoExtension.class) for lifecycle management.");
        }

        if (errors.stream().anyMatch(error -> error.contains("No public test class"))) {
            directives.add("Declare a public test class whose name ends with Test (for example, `OrderTest`).");
        }

        if (errors.stream().anyMatch(error -> error.contains("Missing required imports"))) {
            directives.add("Ensure the imports include org.junit.jupiter.api.* and Mockito static helpers before returning code.");
        }

        return new ArrayList<>(directives);
    }

    private static Optional<String> extractEnumOwner(String error) {
        if (error == null) {
            return Optional.empty();
        }
        Matcher matcher = ENUM_CONSTANT_PATTERN.matcher(error);
        if (matcher.find()) {
            return Optional.ofNullable(matcher.group(1));
        }
        return Optional.empty();
    }

    private static Optional<String> extractUnresolvedType(String error) {
        if (error == null) {
            return Optional.empty();
        }
        Matcher matcher = TYPE_UNRESOLVED_PATTERN.matcher(error);
        if (matcher.find()) {
            return Optional.ofNullable(matcher.group(1));
        }
        return Optional.empty();
    }

    private static Optional<String> extractUnavailablePackage(String error) {
        if (error == null) {
            return Optional.empty();
        }
        Matcher matcher = PACKAGE_UNAVAILABLE_PATTERN.matcher(error);
        if (matcher.find()) {
            return Optional.ofNullable(matcher.group(1));
        }
        return Optional.empty();
    }
}
