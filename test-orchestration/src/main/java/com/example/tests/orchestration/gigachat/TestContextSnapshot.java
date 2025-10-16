package com.example.tests.orchestration.gigachat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Provides the necessary project context to request targeted fixes from Gigachat.
 */
public final class TestContextSnapshot {

    private final String testClassName;
    private final Path sourceFile;
    private final String sourceCode;
    private final Map<String, List<String>> dependencyMethods;
    private final Map<String, List<String>> supportingTypes;
    private final Map<String, List<String>> enumConstants;
    private final List<String> targetMethods;

    public TestContextSnapshot(String testClassName,
                               Path sourceFile,
                               String sourceCode,
                               Map<String, List<String>> dependencyMethods,
                               Map<String, List<String>> supportingTypes,
                               Map<String, List<String>> enumConstants,
                               List<String> targetMethods) {
        this.testClassName = Objects.requireNonNull(testClassName, "testClassName");
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        this.sourceCode = Objects.requireNonNull(sourceCode, "sourceCode");
        this.dependencyMethods = Map.copyOf(Objects.requireNonNull(dependencyMethods, "dependencyMethods"));
        this.supportingTypes = Map.copyOf(Objects.requireNonNull(supportingTypes, "supportingTypes"));
        this.enumConstants = Map.copyOf(Objects.requireNonNull(enumConstants, "enumConstants"));
        this.targetMethods = List.copyOf(Objects.requireNonNull(targetMethods, "targetMethods"));
    }

    public String getTestClassName() {
        return testClassName;
    }

    public Path getSourceFile() {
        return sourceFile;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public Map<String, List<String>> getDependencyMethods() {
        return dependencyMethods;
    }

    public Map<String, List<String>> getSupportingTypes() {
        return supportingTypes;
    }

    public Map<String, List<String>> getEnumConstants() {
        return enumConstants;
    }

    public List<String> getTargetMethods() {
        return targetMethods;
    }

    public TestContextSnapshot withUpdatedSource(String updatedSource) {
        return new TestContextSnapshot(testClassName, sourceFile, updatedSource, dependencyMethods, supportingTypes,
                enumConstants, targetMethods);
    }
}
