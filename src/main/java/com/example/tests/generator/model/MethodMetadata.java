package com.example.tests.generator.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Describes a class method or constructor with its signature and annotations.
 */
public final class MethodMetadata {

    private final String name;
    private final String returnType;
    private final boolean constructor;
    private final boolean staticMethod;
    private final String description;
    private final List<Parameter> parameters;
    private final List<String> annotations;

    private MethodMetadata(Builder builder) {
        this.name = Objects.requireNonNull(builder.name, "name");
        this.returnType = builder.returnType == null ? "void" : builder.returnType;
        this.constructor = builder.constructor;
        this.staticMethod = builder.staticMethod;
        this.description = builder.description == null ? "" : builder.description;
        this.parameters = Collections.unmodifiableList(new ArrayList<>(builder.parameters));
        this.annotations = Collections.unmodifiableList(new ArrayList<>(builder.annotations));
    }

    public String getName() {
        return name;
    }

    public String getReturnType() {
        return returnType;
    }

    public boolean isConstructor() {
        return constructor;
    }

    public boolean isStatic() {
        return staticMethod;
    }

    public String getDescription() {
        return description;
    }

    public List<Parameter> getParameters() {
        return parameters;
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name;
        private String returnType;
        private boolean constructor;
        private boolean staticMethod;
        private String description;
        private final List<Parameter> parameters = new ArrayList<>();
        private final List<String> annotations = new ArrayList<>();

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder returnType(String returnType) {
            this.returnType = returnType;
            return this;
        }

        public Builder constructor(boolean constructor) {
            this.constructor = constructor;
            return this;
        }

        public Builder staticMethod(boolean staticMethod) {
            this.staticMethod = staticMethod;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder addParameter(String type, String name) {
            this.parameters.add(new Parameter(type, name));
            return this;
        }

        public Builder addParameterType(String signature) {
            if (signature != null) {
                String trimmed = signature.trim();
                int space = trimmed.lastIndexOf(' ');
                if (space > 0 && space < trimmed.length() - 1) {
                    addParameter(trimmed.substring(0, space), trimmed.substring(space + 1));
                } else {
                    addParameter(trimmed, "arg" + parameters.size());
                }
            }
            return this;
        }

        public Builder addAnnotation(String annotation) {
            if (annotation != null && !annotation.isBlank()) {
                this.annotations.add(annotation);
            }
            return this;
        }

        public MethodMetadata build() {
            return new MethodMetadata(this);
        }
    }

    public static final class Parameter {
        private final String type;
        private final String name;

        private Parameter(String type, String name) {
            this.type = type == null ? "Object" : type;
            this.name = name == null ? "arg" : name;
        }

        public String getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return type + ' ' + name;
        }
    }
}
