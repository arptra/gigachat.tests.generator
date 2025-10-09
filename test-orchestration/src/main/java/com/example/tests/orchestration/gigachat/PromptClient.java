package com.example.tests.orchestration.gigachat;

import java.util.Map;

@FunctionalInterface
public interface PromptClient {
    String sendPrompt(String prompt, Map<String, String> options);
}
