package com.example.tests.generator.reporting;

/**
 * Represents a single coverage metric from a Jacoco report.
 */
public record CoverageMetric(String type, long missed, long covered) {

    public long total() {
        return missed + covered;
    }

    public double coveragePercentage() {
        long total = total();
        if (total == 0) {
            return 100.0d;
        }
        return (covered * 100.0d) / total;
    }
}
