package com.example.tests.generator.verification;

/**
 * Represents a single rule that can mutate a generated test class to satisfy
 * local coding conventions or compilation requirements.
 */
@FunctionalInterface
public interface GeneratedTestRule {

    void apply(GeneratedTestContext context);
}
