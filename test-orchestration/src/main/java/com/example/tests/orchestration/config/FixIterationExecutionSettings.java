package com.example.tests.orchestration.config;

import java.util.Objects;

/**
 * Configuration toggles that control which Gradle verification steps should run during
 * the fix iteration workflow.
 */
public final class FixIterationExecutionSettings {

    private final boolean runCompilation;
    private final boolean runTestExecution;
    private final boolean methodScopedPrompts;

    public FixIterationExecutionSettings(boolean runCompilation,
                                         boolean runTestExecution,
                                         boolean methodScopedPrompts) {
        this.runCompilation = runCompilation;
        this.runTestExecution = runTestExecution;
        this.methodScopedPrompts = methodScopedPrompts;
    }

    public static FixIterationExecutionSettings defaults() {
        return new FixIterationExecutionSettings(true, true, false);
    }

    public boolean runCompilation() {
        return runCompilation;
    }

    public boolean runTestExecution() {
        return runTestExecution;
    }

    public boolean useMethodScopedPrompts() {
        return methodScopedPrompts;
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
                && runTestExecution == settings.runTestExecution
                && methodScopedPrompts == settings.methodScopedPrompts;
    }

    @Override
    public int hashCode() {
        return Objects.hash(runCompilation, runTestExecution, methodScopedPrompts);
    }

    @Override
    public String toString() {
        return "FixIterationExecutionSettings{"
                + "runCompilation=" + runCompilation
                + ", runTestExecution=" + runTestExecution
                + ", methodScopedPrompts=" + methodScopedPrompts
                + '}';
    }
}
