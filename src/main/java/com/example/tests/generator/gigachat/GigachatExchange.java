package com.example.tests.generator.gigachat;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a Gigachat request/response pair for audit purposes.
 */
public class GigachatExchange {

    private final Instant timestamp;
    private final String request;
    private final String response;

    public GigachatExchange(Instant timestamp, String request, String response) {
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        this.request = Objects.requireNonNull(request, "request");
        this.response = Objects.requireNonNull(response, "response");
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getRequest() {
        return request;
    }

    public String getResponse() {
        return response;
    }
}
