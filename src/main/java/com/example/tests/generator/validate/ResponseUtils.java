package com.example.tests.generator.validate;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper utilities for extracting Java source code fragments from LLM responses.
 */
public final class ResponseUtils {

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(?:java)?\\s*(.*?)```", Pattern.DOTALL);

    private ResponseUtils() {
    }

    public static Optional<String> extractJavaCodeBlock(String response) {
        if (response == null) {
            return Optional.empty();
        }
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(response);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.ofNullable(matcher.group(1)).map(String::trim);
    }
}
