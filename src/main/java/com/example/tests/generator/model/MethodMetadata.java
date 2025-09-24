package com.example.tests.generator.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Describes a method of the target class to help the agent craft tests.
 */
public class MethodMetadata {

    private final String name;
    private final String returnType;
    private final List<String> parameterTypes;
    private final String description;
    private final boolean isStatic;

    private MethodMetadata(Builder builder) {
        this.name = Objects.requireNonNull(builder.name, "name");
        this.returnType = builder.returnType == null ? "void" : builder.returnType;
        this.parameterTypes = Collections.unmodifiableList(new ArrayList<>(builder.parameterTypes));
        this.description = builder.description;
        this.isStatic = builder.isStatic;
    }

    public String getName() {
        return name;
    }

    public String getReturnType() {
        return returnType;
    }

    public List<String> getParameterTypes() {
        return parameterTypes;
    }

    public String getDescription() {
        return description;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String returnType;
        private List<String> parameterTypes = new ArrayList<>();
        private String description;
        private boolean isStatic;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder returnType(String returnType) {
            this.returnType = returnType;
            return this;
        }

        public Builder parameterTypes(List<String> parameterTypes) {
            this.parameterTypes = new ArrayList<>(parameterTypes);
            return this;
        }

        public Builder addParameterType(String parameterType) {
            this.parameterTypes.add(parameterType);
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder isStatic(boolean isStatic) {
            this.isStatic = isStatic;
            return this;
        }

        public MethodMetadata build() {
            return new MethodMetadata(this);
        }
    }
}
