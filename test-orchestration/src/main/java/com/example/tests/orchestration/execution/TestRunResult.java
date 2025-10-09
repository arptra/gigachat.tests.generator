package com.example.tests.orchestration.execution;

import java.time.Duration;
import java.util.Objects;

/**
 * Result of executing a collection of tests.
 */
public final class TestRunResult {

    private final int exitCode;
    private final boolean timedOut;
    private final Duration elapsedTime;
    private final String output;

    public TestRunResult(int exitCode, boolean timedOut, Duration elapsedTime, String output) {
        this.exitCode = exitCode;
        this.timedOut = timedOut;
        this.elapsedTime = Objects.requireNonNull(elapsedTime, "elapsedTime");
        this.output = Objects.requireNonNull(output, "output");
    }

    public int getExitCode() {
        return exitCode;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public Duration getElapsedTime() {
        return elapsedTime;
    }

    public String getOutput() {
        return output;
    }

    public boolean isSuccessful() {
        return exitCode == 0 && !timedOut;
    }
}
