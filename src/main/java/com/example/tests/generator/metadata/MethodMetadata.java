package com.example.tests.generator.metadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Describes a public API method of the class under test.
 */
public final class MethodMetadata {

    private final String name;
    private final String returnType;
    private final List<ParameterMetadata> parameters;
    private final String description;
    private final boolean staticMethod;
    private final boolean constructor;

    private MethodMetadata(Builder builder) {
        this.name = Objects.requireNonNull(builder.name, "name");
        this.returnType = Objects.requireNonNull(builder.returnType, "returnType");
        this.parameters = Collections.unmodifiableList(new ArrayList<>(builder.parameters));
        this.description = builder.description == null ? "" : builder.description;
        this.staticMethod = builder.staticMethod;
        this.constructor = builder.constructor;
    }

    public String getName() {
        return name;
    }

    public String getReturnType() {
        return returnType;
    }

    public List<ParameterMetadata> getParameters() {
        return parameters;
    }

    public String getDescription() {
        return description;
    }

    public boolean isStaticMethod() {
        return staticMethod;
    }

    public boolean isConstructor() {
        return constructor;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name;
        private String returnType;
        private final List<ParameterMetadata> parameters = new ArrayList<>();
        private String description;
        private boolean staticMethod;
        private boolean constructor;

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

        public Builder addParameter(ParameterMetadata parameter) {
            this.parameters.add(Objects.requireNonNull(parameter, "parameter"));
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder staticMethod(boolean staticMethod) {
            this.staticMethod = staticMethod;
            return this;
        }

        public Builder constructor(boolean constructor) {
            this.constructor = constructor;
            return this;
        }

        public MethodMetadata build() {
            return new MethodMetadata(this);
        }
    }
}
