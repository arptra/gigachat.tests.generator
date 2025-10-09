package com.example.tests.orchestration.execution;

/**
 * Executes all requested tests and returns the aggregated run result.
 */
public interface TestSuiteRunner {

    TestRunResult runAllTests(TestRunRequest request);
}
