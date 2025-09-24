package com.example.tests.generator.validate;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper utilities for extracting Java source code fragments from LLM responses.
 */
public final class ResponseUtils {

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(?:java)?\\s*(.*?)```", Pattern.DOTALL);
    private static final Pattern CLASS_DECLARATION_PATTERN = Pattern.compile("\\bclass\\s+\\w+Test\\b");

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

    public static Optional<String> extractJavaSnippet(String response) {
        if (response == null) {
            return Optional.empty();
        }
        if (!CLASS_DECLARATION_PATTERN.matcher(response).find()) {
            return Optional.empty();
        }
        int packageIndex = response.indexOf("package ");
        int importIndex = response.indexOf("import ");
        int classIndex = response.indexOf("class ");
        int startIndex = -1;
        if (packageIndex >= 0) {
            startIndex = packageIndex;
        }
        if (importIndex >= 0 && (startIndex < 0 || importIndex < startIndex)) {
            startIndex = importIndex;
        }
        if (classIndex >= 0 && (startIndex < 0 || classIndex < startIndex)) {
            startIndex = classIndex;
        }
        if (startIndex < 0) {
            return Optional.empty();
        }
        String snippet = response.substring(startIndex).trim();
        if (snippet.isEmpty()) {
            return Optional.empty();
        }
        int lastBrace = snippet.lastIndexOf('}');
        if (lastBrace > 0) {
            snippet = snippet.substring(0, lastBrace + 1);
        }
        return snippet.isEmpty() ? Optional.empty() : Optional.of(snippet);
    }
}
