package com.example.tests.generator.gigachat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Collects Gigachat request/response pairs for later analysis or auditing.
 */
public class GigachatAuditLogger {

    private final List<GigachatExchange> entries = new ArrayList<>();

    public void log(String request, String response) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(response, "response");
        entries.add(new GigachatExchange(Instant.now(), request, response));
    }

    public List<GigachatExchange> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public void reset() {
        entries.clear();
    }
}
