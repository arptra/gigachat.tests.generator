package com.example.tests.orchestration.reporting;

import java.util.List;
import java.util.Objects;

/**
 * Describes a single failing test case, including the assertion message and diagnostic snippet.
 */
public final class TestFailureDetail {

    private final String testClass;
    private final String testMethod;
    private final String message;
    private final List<String> diagnostics;

    public TestFailureDetail(String testClass, String testMethod, String message, List<String> diagnostics) {
        this.testClass = Objects.requireNonNull(testClass, "testClass");
        this.testMethod = Objects.requireNonNull(testMethod, "testMethod");
        this.message = Objects.requireNonNull(message, "message");
        this.diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    public String getTestClass() {
        return testClass;
    }

    public String getTestMethod() {
        return testMethod;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getDiagnostics() {
        return diagnostics;
    }
}
