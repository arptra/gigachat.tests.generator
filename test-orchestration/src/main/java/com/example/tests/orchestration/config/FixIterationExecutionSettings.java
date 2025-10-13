package com.example.tests.orchestration.config;

import java.util.Objects;

/**
 * Configuration toggles that control which Gradle verification steps should run during
 * the fix iteration workflow.
 */
public final class FixIterationExecutionSettings {

    private final boolean runCompilation;
    private final boolean runTestExecution;

    public FixIterationExecutionSettings(boolean runCompilation, boolean runTestExecution) {
        this.runCompilation = runCompilation;
        this.runTestExecution = runTestExecution;
    }

    public static FixIterationExecutionSettings defaults() {
        return new FixIterationExecutionSettings(true, true);
    }

    public boolean runCompilation() {
        return runCompilation;
    }

    public boolean runTestExecution() {
        return runTestExecution;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FixIterationExecutionSettings settings)) {
            return false;
        }
        return runCompilation == settings.runCompilation
                && runTestExecution == settings.runTestExecution;
    }

    @Override
    public int hashCode() {
        return Objects.hash(runCompilation, runTestExecution);
    }

    @Override
    public String toString() {
        return "FixIterationExecutionSettings{"
                + "runCompilation=" + runCompilation
                + ", runTestExecution=" + runTestExecution
                + '}';
    }
}
