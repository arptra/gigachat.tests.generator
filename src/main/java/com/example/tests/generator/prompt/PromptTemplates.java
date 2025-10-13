package com.example.tests.generator.prompt;

import com.example.tests.generator.metadata.ClassMetadata;
import com.example.tests.generator.metadata.MethodMetadata;
import com.example.tests.generator.metadata.ParameterMetadata;
import com.example.tests.generator.metadata.RelatedTypeMetadata;
import com.example.tests.generator.model.ClassKind;

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
            "You are generating unit tests for the %s `%s` located in package `%s`. " +
            "The class is described as follows:%n%s%n";

    private static final String METHOD_TEMPLATE =
            "  - %s%s %s(%s)%s";

    private static final String DEPENDENCY_TEMPLATE =
            "The class collaborates with the following dependencies:%n%s";

    private static final String TEST_REQUIREMENTS_TEMPLATE =
            "Write JUnit Jupiter tests that follow AAA (Arrange-Act-Assert). " +
            "Use the Mockito extension for mocking and prefer constructor injection. " +
            "Always import `org.junit.jupiter.api.Test`, `org.junit.jupiter.api.Assertions`, " +
            "and Mockito static methods from `org.mockito.Mockito`. Explicitly import every other annotation or dependency used in the test so it compiles cleanly. When possible rely on `@ExtendWith(MockitoExtension.class)`. " +
            "Use only the exact public API described below—if a constructor or method is not listed, it must not be used. " +
            "Do not assume production classes expose additional getters, setters, or fields beyond what is documented. " +
            "Instantiate types only via the documented constructors and do not add extra supporting implementations or stand-in domain objects beyond what is described. " +
            "Interfaces or abstract types must be mocked with Mockito instead of being instantiated. " +
            "Enums may only be referenced via their declared constants; never call `new` on an enum. " +
            "Do not introduce additional assertion libraries such as AssertJ. " +
            "Respond only with the complete Java test class wrapped in a ```java``` code block without additional explanations.";

    private static final String COVERAGE_TEMPLATE =
            "Target coverage:%n%s";

    private static final String MOCKING_TEMPLATE =
            "Mocking restrictions:%n%s";

    private static final String EXAMPLES_TEMPLATE =
            "Example scenarios to cover:%n%s";

    private static final String ENUM_CONSTANTS_TEMPLATE =
            "Enum constants:%n%s";

    public static String renderClassDescription(ClassMetadata metadata) {
        String methods = metadata.getMethods().isEmpty()
                ? "  (no public methods were described)"
                : metadata.getMethods().stream()
                        .map(PromptTemplates::renderMethod)
                        .collect(Collectors.joining(System.lineSeparator()));
        String typeLabel = describeKind(metadata.getKind(), metadata.isAbstractType());
        StringBuilder builder = new StringBuilder(String.format(Locale.ENGLISH, CLASS_DESCRIPTION_TEMPLATE,
                typeLabel, metadata.getClassName(), metadata.getPackageName(), metadata.getDescription()));
        builder.append("Public API methods:").append(System.lineSeparator()).append(methods).append(System.lineSeparator());
        if (metadata.isInterface()) {
            builder.append("This type is an interface; use Mockito mocks or explicit stubs instead of direct instantiation.")
                    .append(System.lineSeparator());
        } else if (metadata.isAbstractType()) {
            builder.append("The class is abstract; cover behaviour through its public API and mock collaborators as needed.")
                    .append(System.lineSeparator());
        }
        return builder.toString();
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

    public static String renderEnumConstants(ClassMetadata metadata) {
        if (!metadata.isEnumType()) {
            return "";
        }
        String body;
        if (metadata.getEnumConstants().isEmpty()) {
            body = "  - (not documented—ask for the declared values before referencing them)";
        } else {
            body = metadata.getEnumConstants().stream()
                    .map(constant -> "  - " + constant)
                    .collect(Collectors.joining(System.lineSeparator()));
        }
        return String.format(Locale.ENGLISH, ENUM_CONSTANTS_TEMPLATE, body) + System.lineSeparator();
    }

    public static String renderSupportingTypes(ClassMetadata metadata) {
        if (metadata.getSupportingTypes().isEmpty()) {
            return "";
        }
        String body = metadata.getSupportingTypes().stream()
                .map(PromptTemplates::renderSupportingType)
                .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()));
        return "Supporting domain types:" + System.lineSeparator() + body + System.lineSeparator() + System.lineSeparator();
    }

    private static String renderMethod(MethodMetadata method) {
        String parameters = method.getParameters().stream()
                .map(ParameterMetadata::toString)
                .collect(Collectors.joining(", "));
        String description = method.getDescription().isEmpty()
                ? ""
                : " // " + method.getDescription();
        String qualifier;
        if (method.isConstructor()) {
            qualifier = "constructor ";
        } else if (method.isStaticMethod()) {
            qualifier = "static ";
        } else {
            qualifier = "instance ";
        }
        return String.format(Locale.ENGLISH, METHOD_TEMPLATE,
                qualifier,
                method.getReturnType(),
                method.getName(), parameters, description);
    }

    private static String renderSupportingType(RelatedTypeMetadata type) {
        StringBuilder builder = new StringBuilder();
        builder.append("- ").append(describeKind(type.getKind(), type.isAbstractType())).append(' ')
                .append(type.getQualifiedName()).append(System.lineSeparator());
        if (type.isEnumType()) {
            builder.append("  Enum constants:").append(System.lineSeparator());
            if (type.getEnumConstants().isEmpty()) {
                builder.append("    - (not documented—ask for the declared values before referencing them)")
                        .append(System.lineSeparator());
            } else {
                type.getEnumConstants().forEach(constant -> builder.append("    - ").append(constant)
                        .append(System.lineSeparator()));
            }
        }
        if (!type.getMethods().isEmpty()) {
            builder.append("  Public API:").append(System.lineSeparator());
            type.getMethods().stream()
                    .map(PromptTemplates::renderMethod)
                    .map(line -> "    " + line.trim())
                    .forEach(line -> builder.append(line).append(System.lineSeparator()));
        }
        return builder.toString().trim();
    }

    private static String describeKind(ClassKind kind, boolean abstractType) {
        if (kind == null) {
            return abstractType ? "abstract class" : "class";
        }
        return switch (kind) {
            case ENUM -> "enum";
            case INTERFACE -> "interface";
            case RECORD -> "record";
            default -> abstractType ? "abstract class" : "class";
        };
    }
}
