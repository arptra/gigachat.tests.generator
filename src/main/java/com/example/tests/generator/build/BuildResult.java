package com.example.tests.generator.build;

import com.example.tests.generator.reporting.CoverageSummary;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of a build validation run.
 */
public class BuildResult {

    private final BuildTool buildTool;
    private final boolean success;
    private final String output;
    private final List<String> errors;
    private final CoverageSummary coverageSummary;
    private final ErrorReport errorReport;

    public BuildResult(BuildTool buildTool,
                       boolean success,
                       String output,
                       List<String> errors,
                       CoverageSummary coverageSummary,
                       ErrorReport errorReport) {
        this.buildTool = Objects.requireNonNull(buildTool, "buildTool");
        this.success = success;
        this.output = Objects.requireNonNull(output, "output");
        this.errors = List.copyOf(errors);
        this.coverageSummary = coverageSummary;
        this.errorReport = errorReport;
    }

    public BuildTool getBuildTool() {
        return buildTool;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getOutput() {
        return output;
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public Optional<CoverageSummary> getCoverageSummary() {
        return Optional.ofNullable(coverageSummary);
    }

    public Optional<ErrorReport> getErrorReport() {
        return Optional.ofNullable(errorReport);
    }
}
