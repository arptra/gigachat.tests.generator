package com.example.tests.orchestration.reporting;

import com.example.tests.orchestration.execution.TestRunResult;

import java.util.List;
import java.util.Objects;

/**
 * Collects failing test cases from a {@link TestRunResult} by parsing the execution output.
 */
public final class TestFailureCollector {

    private final GradleTestFailureParser parser;

    public TestFailureCollector() {
        this(new GradleTestFailureParser());
    }

    public TestFailureCollector(GradleTestFailureParser parser) {
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    public List<TestFailureDetail> collectFailures(TestRunResult result) {
        Objects.requireNonNull(result, "result");
        return parser.parse(result.getOutput());
    }
}
