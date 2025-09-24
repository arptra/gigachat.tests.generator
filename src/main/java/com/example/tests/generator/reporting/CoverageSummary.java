package com.example.tests.generator.reporting;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Summary of coverage metrics extracted from a Jacoco XML report.
 */
public class CoverageSummary {

    private final Path reportPath;
    private final Map<String, CoverageMetric> metrics;

    public CoverageSummary(Path reportPath, Map<String, CoverageMetric> metrics) {
        this.reportPath = Objects.requireNonNull(reportPath, "reportPath");
        this.metrics = Collections.unmodifiableMap(new LinkedHashMap<>(metrics));
    }

    public Path getReportPath() {
        return reportPath;
    }

    public Map<String, CoverageMetric> getMetrics() {
        return metrics;
    }

    public String formatSummary() {
        return metrics.values().stream()
                .map(metric -> metric.type() + "=" + String.format(Locale.ROOT, "%.2f%%", metric.coveragePercentage()))
                .collect(Collectors.joining(", "));
    }
}
