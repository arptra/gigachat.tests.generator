package com.example.tests.orchestration.gigachat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents the ongoing interaction with Gigachat for a single failing test.
 */
public final class FixConversationSession {

    private final List<String> exchanges = new ArrayList<>();
    private final String initialPrompt;

    public FixConversationSession(String initialPrompt) {
        this.initialPrompt = Objects.requireNonNull(initialPrompt, "initialPrompt");
    }

    public void recordModelResponse(String response) {
        exchanges.add(Objects.requireNonNull(response, "response"));
    }

    public List<String> getExchanges() {
        return List.copyOf(exchanges);
    }

    public String getInitialPrompt() {
        return initialPrompt;
    }
}
