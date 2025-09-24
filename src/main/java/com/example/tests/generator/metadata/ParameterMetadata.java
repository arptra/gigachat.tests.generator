package com.example.tests.generator.metadata;

import java.util.Objects;

/**
 * Describes a single method parameter.
 */
public final class ParameterMetadata {

    private final String name;
    private final String type;

    private ParameterMetadata(Builder builder) {
        this.name = Objects.requireNonNull(builder.name, "name");
        this.type = Objects.requireNonNull(builder.type, "type");
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return type + " " + name;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name;
        private String type;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public ParameterMetadata build() {
            return new ParameterMetadata(this);
        }
    }
}
