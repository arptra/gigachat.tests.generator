package com.example.tests.generator.method;

import java.util.Objects;

/**
 * Context passed to mock rules while processing a method.
 */
public record MethodAnalysisContext(String className, String methodName) {

    public MethodAnalysisContext {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(methodName, "methodName");
    }
}
