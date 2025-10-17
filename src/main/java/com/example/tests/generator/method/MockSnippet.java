package com.example.tests.generator.method;

import java.util.Objects;

/**
 * Represents a suggested mock for a specific code segment.
 */
public record MockSnippet(String ruleId, String originalCode, String mockCode) {

    public MockSnippet {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(originalCode, "originalCode");
        Objects.requireNonNull(mockCode, "mockCode");
    }
}
