package com.example.tests.generator.prompt;

import com.example.tests.generator.metadata.ClassMetadata;
import com.example.tests.generator.metadata.MethodMetadata;
import com.example.tests.generator.metadata.ParameterMetadata;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Holds reusable prompt templates for describing the class under test and the
 * expectations towards the generated unit tests.
 */
public final class PromptTemplates {

    private PromptTemplates() {
    }

    private static final String CLASS_DESCRIPTION_TEMPLATE =
            "You are generating unit tests for the class `%s` located in package `%s`. " +
            "The class is described as follows:%n%s%n";

    private static final String METHOD_TEMPLATE =
            "  - %s %s(%s)%s";

    private static final String DEPENDENCY_TEMPLATE =
            "The class collaborates with the following dependencies:%n%s";

    private static final String TEST_REQUIREMENTS_TEMPLATE =
            "Write JUnit Jupiter tests that follow AAA (Arrange-Act-Assert). " +
            "Use the Mockito extension for mocking and prefer constructor injection. " +
            "Always import `org.junit.jupiter.api.Test`, `org.junit.jupiter.api.Assertions`, " +
            "and Mockito static helpers from `org.mockito.Mockito`. When possible rely on `@ExtendWith(MockitoExtension.class)`. " +
            "Respond only with the complete Java test class wrapped in a ```java``` code block without additional explanations.";

    private static final String COVERAGE_TEMPLATE =
            "Target coverage:%n%s";

    private static final String MOCKING_TEMPLATE =
            "Mocking restrictions:%n%s";

    private static final String EXAMPLES_TEMPLATE =
            "Example scenarios to cover:%n%s";

    public static String renderClassDescription(ClassMetadata metadata) {
        String methods = metadata.getMethods().isEmpty()
                ? "  (no public methods were described)"
                : metadata.getMethods().stream()
                        .map(PromptTemplates::renderMethod)
                        .collect(Collectors.joining(System.lineSeparator()));
        return String.format(Locale.ENGLISH, CLASS_DESCRIPTION_TEMPLATE, metadata.getClassName(),
                metadata.getPackageName(), metadata.getDescription())
                + "Public API methods:" + System.lineSeparator() + methods + System.lineSeparator();
    }

    public static String renderDependencies(List<String> dependencies) {
        if (dependencies.isEmpty()) {
            return "The class does not depend on external collaborators." + System.lineSeparator();
        }
        String body = dependencies.stream()
                .map(dep -> "  - " + dep)
                .collect(Collectors.joining(System.lineSeparator()));
        return String.format(Locale.ENGLISH, DEPENDENCY_TEMPLATE, body) + System.lineSeparator();
    }

    public static String renderTestRequirements() {
        return TEST_REQUIREMENTS_TEMPLATE + System.lineSeparator();
    }

    public static String renderCoverageSection(ClassMetadata metadata) {
        if (metadata.getCoverageRequirements() == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (metadata.getCoverageRequirements().hasLineCoverage()) {
            builder.append(String.format(Locale.ENGLISH, "  - Line coverage: %.0f%%%n",
                    metadata.getCoverageRequirements().getLineCoverage() * 100));
        }
        if (metadata.getCoverageRequirements().hasBranchCoverage()) {
            builder.append(String.format(Locale.ENGLISH, "  - Branch coverage: %.0f%%%n",
                    metadata.getCoverageRequirements().getBranchCoverage() * 100));
        }
        metadata.getCoverageRequirements().getAdditionalCriteria().forEach(
                criterion -> builder.append("  - ").append(criterion).append(System.lineSeparator()));
        if (builder.length() == 0) {
            return "";
        }
        return String.format(Locale.ENGLISH, COVERAGE_TEMPLATE, builder) + System.lineSeparator();
    }

    public static String renderMockingRestrictions(ClassMetadata metadata) {
        List<String> restrictions = metadata.getMockingRestrictions();
        if (restrictions.isEmpty()) {
            return "";
        }
        String body = restrictions.stream()
                .map(restriction -> "  - " + restriction)
                .collect(Collectors.joining(System.lineSeparator()));
        return String.format(Locale.ENGLISH, MOCKING_TEMPLATE, body) + System.lineSeparator();
    }

    public static String renderExampleScenarios(ClassMetadata metadata) {
        List<String> examples = metadata.getExampleScenarios();
        if (examples.isEmpty()) {
            return "";
        }
        String body = examples.stream()
                .map(example -> "  - " + example)
                .collect(Collectors.joining(System.lineSeparator()));
        return String.format(Locale.ENGLISH, EXAMPLES_TEMPLATE, body) + System.lineSeparator();
    }

    private static String renderMethod(MethodMetadata method) {
        String parameters = method.getParameters().stream()
                .map(ParameterMetadata::toString)
                .collect(Collectors.joining(", "));
        String description = method.getDescription().isEmpty()
                ? ""
                : " // " + method.getDescription();
        return String.format(Locale.ENGLISH, METHOD_TEMPLATE,
                method.isStaticMethod() ? "static" : "instance",
                method.getName(), parameters, description);
    }
}
