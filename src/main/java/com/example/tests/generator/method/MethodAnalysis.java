package com.example.tests.generator.method;

import java.util.List;
import java.util.Objects;

/**
 * Represents the analyzed structure of a single test method.
 */
public record MethodAnalysis(String className,
                             String methodName,
                             String methodSource,
                             List<LogicalCodeUnit> codeUnits) {

    public MethodAnalysis {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(methodSource, "methodSource");
        Objects.requireNonNull(codeUnits, "codeUnits");
        codeUnits = List.copyOf(codeUnits);
    }
}
