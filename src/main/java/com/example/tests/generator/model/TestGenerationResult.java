package com.example.tests.generator.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregates the outputs returned by the Gigachat based agent.
 */
public class TestGenerationResult {

    private final String analysis;
    private final String draftTests;
    private final List<String> streamedChunks;

    public TestGenerationResult(String analysis, String draftTests, List<String> streamedChunks) {
        this.analysis = analysis;
        this.draftTests = draftTests;
        this.streamedChunks = Collections.unmodifiableList(new ArrayList<>(streamedChunks));
    }

    public String getAnalysis() {
        return analysis;
    }

    public String getDraftTests() {
        return draftTests;
    }

    public List<String> getStreamedChunks() {
        return streamedChunks;
    }
}
