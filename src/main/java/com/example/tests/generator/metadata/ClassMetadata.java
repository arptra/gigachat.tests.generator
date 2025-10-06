package com.example.tests.generator.metadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Describes the production class that needs tests.
 */
public final class ClassMetadata {

    private final String packageName;
    private final String className;
    private final String description;
    private final List<MethodMetadata> methods;
    private final List<String> dependencies;
    private final CoverageRequirements coverageRequirements;
    private final List<String> mockingRestrictions;
    private final List<String> exampleScenarios;
    private final boolean enumType;
    private final List<String> enumConstants;

    private ClassMetadata(Builder builder) {
        this.packageName = Objects.requireNonNull(builder.packageName, "packageName");
        this.className = Objects.requireNonNull(builder.className, "className");
        this.description = builder.description == null ? "" : builder.description;
        this.methods = Collections.unmodifiableList(new ArrayList<>(builder.methods));
        this.dependencies = Collections.unmodifiableList(new ArrayList<>(builder.dependencies));
        this.coverageRequirements = builder.coverageRequirements;
        this.mockingRestrictions = Collections.unmodifiableList(new ArrayList<>(builder.mockingRestrictions));
        this.exampleScenarios = Collections.unmodifiableList(new ArrayList<>(builder.exampleScenarios));
        this.enumType = builder.enumType;
        this.enumConstants = Collections.unmodifiableList(new ArrayList<>(builder.enumConstants));
    }

    public String getPackageName() {
        return packageName;
    }

    public String getClassName() {
        return className;
    }

    public String getDescription() {
        return description;
    }

    public List<MethodMetadata> getMethods() {
        return methods;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public CoverageRequirements getCoverageRequirements() {
        return coverageRequirements;
    }

    public List<String> getMockingRestrictions() {
        return mockingRestrictions;
    }

    public List<String> getExampleScenarios() {
        return exampleScenarios;
    }

    public boolean isEnumType() {
        return enumType;
    }

    public List<String> getEnumConstants() {
        return enumConstants;
    }

    public String getFullyQualifiedName() {
        return packageName + "." + className;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String packageName;
        private String className;
        private String description;
        private final List<MethodMetadata> methods = new ArrayList<>();
        private final List<String> dependencies = new ArrayList<>();
        private CoverageRequirements coverageRequirements;
        private final List<String> mockingRestrictions = new ArrayList<>();
        private final List<String> exampleScenarios = new ArrayList<>();
        private boolean enumType;
        private final List<String> enumConstants = new ArrayList<>();

        private Builder() {
        }

        public Builder packageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public Builder className(String className) {
            this.className = className;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder addMethod(MethodMetadata method) {
            this.methods.add(Objects.requireNonNull(method, "method"));
            return this;
        }

        public Builder addDependency(String dependency) {
            this.dependencies.add(Objects.requireNonNull(dependency, "dependency"));
            return this;
        }

        public Builder coverageRequirements(CoverageRequirements coverageRequirements) {
            this.coverageRequirements = coverageRequirements;
            return this;
        }

        public Builder addMockingRestriction(String restriction) {
            this.mockingRestrictions.add(Objects.requireNonNull(restriction, "restriction"));
            return this;
        }

        public Builder addExampleScenario(String scenario) {
            this.exampleScenarios.add(Objects.requireNonNull(scenario, "scenario"));
            return this;
        }

        public Builder enumType(boolean enumType) {
            this.enumType = enumType;
            return this;
        }

        public Builder addEnumConstant(String constant) {
            this.enumConstants.add(Objects.requireNonNull(constant, "constant"));
            return this;
        }

        public ClassMetadata build() {
            return new ClassMetadata(this);
        }
    }
}
