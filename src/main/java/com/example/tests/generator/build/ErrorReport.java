package com.example.tests.generator.build;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Describes build failures detected when validating generated tests.
 */
public class ErrorReport {

    private final Instant timestamp;
    private final BuildTool buildTool;
    private final List<String> errors;
    private final String buildLog;

    public ErrorReport(Instant timestamp, BuildTool buildTool, List<String> errors, String buildLog) {
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        this.buildTool = Objects.requireNonNull(buildTool, "buildTool");
        this.errors = List.copyOf(errors);
        this.buildLog = Objects.requireNonNull(buildLog, "buildLog");
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public BuildTool getBuildTool() {
        return buildTool;
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public String getBuildLog() {
        return buildLog;
    }
}
