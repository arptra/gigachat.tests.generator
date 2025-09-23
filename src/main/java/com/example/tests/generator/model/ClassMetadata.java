package com.example.tests.generator.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents metadata about a class under test which will be provided to the LLM agent.
 */
public class ClassMetadata {

    private final String packageName;
    private final String className;
    private final String description;
    private final List<MethodMetadata> methods;
    private final List<String> dependencies;

    private ClassMetadata(Builder builder) {
        this.packageName = builder.packageName;
        this.className = Objects.requireNonNull(builder.className, "className");
        this.description = builder.description;
        this.methods = Collections.unmodifiableList(new ArrayList<>(builder.methods));
        this.dependencies = Collections.unmodifiableList(new ArrayList<>(builder.dependencies));
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String packageName;
        private String className;
        private String description;
        private List<MethodMetadata> methods = new ArrayList<>();
        private List<String> dependencies = new ArrayList<>();

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

        public Builder methods(List<MethodMetadata> methods) {
            this.methods = new ArrayList<>(methods);
            return this;
        }

        public Builder addMethod(MethodMetadata methodMetadata) {
            this.methods.add(methodMetadata);
            return this;
        }

        public Builder dependencies(List<String> dependencies) {
            this.dependencies = new ArrayList<>(dependencies);
            return this;
        }

        public Builder addDependency(String dependency) {
            this.dependencies.add(dependency);
            return this;
        }

        public ClassMetadata build() {
            return new ClassMetadata(this);
        }
    }
}
