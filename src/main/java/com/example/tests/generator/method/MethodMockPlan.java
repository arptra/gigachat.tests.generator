package com.example.tests.generator.method;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregates mock snippets produced for a method.
 */
public record MethodMockPlan(String className,
                             String methodName,
                             String methodSource,
                             List<MockSnippet> snippets,
                             Optional<String> validationFeedback) {

    public MethodMockPlan {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(methodSource, "methodSource");
        Objects.requireNonNull(snippets, "snippets");
        snippets = List.copyOf(snippets);
        validationFeedback = validationFeedback == null ? Optional.empty() : validationFeedback;
    }

    public boolean isEmpty() {
        return snippets.isEmpty();
    }
}
