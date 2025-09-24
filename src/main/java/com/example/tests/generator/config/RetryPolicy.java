package com.example.tests.generator.config;

import java.time.Duration;

/**
 * Simple configuration holder describing retry strategy for Gigachat API calls.
 */
public class RetryPolicy {

    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final double multiplier;

    public RetryPolicy(int maxAttempts, Duration initialBackoff, Duration maxBackoff, double multiplier) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff == null ? Duration.ofSeconds(1) : initialBackoff;
        this.maxBackoff = maxBackoff == null ? Duration.ofSeconds(30) : maxBackoff;
        this.multiplier = multiplier <= 1.0 ? 2.0 : multiplier;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Duration getInitialBackoff() {
        return initialBackoff;
    }

    public Duration getMaxBackoff() {
        return maxBackoff;
    }

    public double getMultiplier() {
        return multiplier;
    }
}
