package com.example.tests.orchestration.fix;

import java.util.Locale;
import java.util.Optional;

/**
 * Extracts Java code blocks from Gigachat responses so only compilable sources are applied.
 */
public final class GigachatResponseParser {

    public Optional<String> extractJavaCode(String response) {
        if (response == null) {
            return Optional.empty();
        }
        String lower = response.toLowerCase(Locale.ROOT);
        int start = lower.indexOf("```java");
        if (start < 0) {
            return Optional.empty();
        }
        int codeStart = start + "```java".length();
        int end = lower.indexOf("```", codeStart);
        if (end < 0) {
            return Optional.empty();
        }
        String code = response.substring(codeStart, end).strip();
        return code.isEmpty() ? Optional.empty() : Optional.of(code);
    }
}
