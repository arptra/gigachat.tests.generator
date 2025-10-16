package com.example.tests.orchestration.analysis;

import java.util.Objects;

/**
 * Represents a single argument passed to a constructor or method invocation.
 */
public final class InvocationArgument {

    private final String type;
    private final String expression;

    public InvocationArgument(String type, String expression) {
        this.type = type == null ? "Unknown" : type;
        this.expression = Objects.requireNonNull(expression, "expression");
    }

    public String getType() {
        return type;
    }

    public String getExpression() {
        return expression;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InvocationArgument that)) {
            return false;
        }
        return Objects.equals(type, that.type) && Objects.equals(expression, that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, expression);
    }

    @Override
    public String toString() {
        return type + " " + expression;
    }
}
