package com.example.tests.generator.method;

import java.util.Objects;

/**
 * Describes an atomic logical unit of code inside a method body.
 */
public record LogicalCodeUnit(int index, String source) {

    public LogicalCodeUnit {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        Objects.requireNonNull(source, "source");
    }
}
