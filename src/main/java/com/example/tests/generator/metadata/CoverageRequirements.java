package com.example.tests.generator.metadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Coverage thresholds and additional expectations for generated tests.
 */
public final class CoverageRequirements {

    private final Double lineCoverage;
    private final Double branchCoverage;
    private final List<String> additionalCriteria;

    private CoverageRequirements(Builder builder) {
        this.lineCoverage = builder.lineCoverage;
        this.branchCoverage = builder.branchCoverage;
        this.additionalCriteria = Collections.unmodifiableList(new ArrayList<>(builder.additionalCriteria));
    }

    public Double getLineCoverage() {
        return lineCoverage;
    }

    public Double getBranchCoverage() {
        return branchCoverage;
    }

    public List<String> getAdditionalCriteria() {
        return additionalCriteria;
    }

    public boolean hasLineCoverage() {
        return lineCoverage != null;
    }

    public boolean hasBranchCoverage() {
        return branchCoverage != null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Double lineCoverage;
        private Double branchCoverage;
        private final List<String> additionalCriteria = new ArrayList<>();

        private Builder() {
        }

        public Builder lineCoverage(Double lineCoverage) {
            this.lineCoverage = lineCoverage;
            return this;
        }

        public Builder branchCoverage(Double branchCoverage) {
            this.branchCoverage = branchCoverage;
            return this;
        }

        public Builder addAdditionalCriterion(String value) {
            this.additionalCriteria.add(value);
            return this;
        }

        public CoverageRequirements build() {
            return new CoverageRequirements(this);
        }
    }
}
