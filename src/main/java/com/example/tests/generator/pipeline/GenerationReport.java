package com.example.tests.generator.pipeline;

import com.example.tests.generator.build.BuildResult;
import com.example.tests.generator.build.ErrorReport;
import com.example.tests.generator.gigachat.GigachatExchange;
import com.example.tests.generator.reporting.CoverageSummary;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Encapsulates the result of a test generation iteration.
 */
public class GenerationReport {

    private final BuildResult buildResult;
    private final List<String> classesWithoutTests;
    private final List<GigachatExchange> auditLog;

    public GenerationReport(BuildResult buildResult,
                            List<String> classesWithoutTests,
                            List<GigachatExchange> auditLog) {
        this.buildResult = Objects.requireNonNull(buildResult, "buildResult");
        this.classesWithoutTests = Collections.unmodifiableList(List.copyOf(classesWithoutTests));
        this.auditLog = Collections.unmodifiableList(List.copyOf(auditLog));
    }

    public boolean isSuccessful() {
        return buildResult.isSuccess();
    }

    public Optional<ErrorReport> getErrorReport() {
        return buildResult.getErrorReport();
    }

    public Optional<CoverageSummary> getCoverageSummary() {
        return buildResult.getCoverageSummary();
    }

    public List<String> getClassesWithoutTests() {
        return classesWithoutTests;
    }

    public List<GigachatExchange> getAuditLog() {
        return auditLog;
    }

    public BuildResult getBuildResult() {
        return buildResult;
    }
}
