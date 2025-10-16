package com.example.tests.orchestration.analysis;

import java.util.Objects;

/**
 * Describes a parameter declared on the analysed method.
 */
public final class MethodParameter {

    private final String name;
    private final String type;

    public MethodParameter(String name, String type) {
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MethodParameter that)) {
            return false;
        }
        return Objects.equals(name, that.name) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type);
    }

    @Override
    public String toString() {
        return type + " " + name;
    }
}
