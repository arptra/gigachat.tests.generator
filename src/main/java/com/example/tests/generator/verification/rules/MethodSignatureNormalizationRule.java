package com.example.tests.generator.verification.rules;

import com.example.tests.generator.verification.GeneratedTestContext;
import com.example.tests.generator.verification.GeneratedTestRule;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalises signatures of generated JUnit test methods to the conventional
 * {@code public void methodName()} form.
 */
public final class MethodSignatureNormalizationRule implements GeneratedTestRule {

    private static final Pattern TEST_METHOD_PATTERN = Pattern.compile(
            "(@Test(?:\\s*\\R\\s*@\\w+[^\\r\\n]*)*\\s*)(public\\s+)([\\w<>\\[\\],\\s]+?)\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*\\{",
            Pattern.MULTILINE);

    @Override
    public void apply(GeneratedTestContext context) {
        Objects.requireNonNull(context, "context");
        String source = context.getSourceCode();
        Matcher matcher = TEST_METHOD_PATTERN.matcher(source);
        StringBuffer buffer = new StringBuffer();
        boolean modified = false;
        while (matcher.find()) {
            String originalReturn = matcher.group(3).trim();
            String originalParameters = matcher.group(5);
            boolean hasParameters = originalParameters != null && !originalParameters.trim().isEmpty();
            boolean shouldNormalizeReturn = !"void".equals(originalReturn)
                    && !containsValueReturn(source, matcher.end());
            boolean shouldRemoveParameters = hasParameters;
            if (shouldNormalizeReturn || shouldRemoveParameters) {
                String newReturn = shouldNormalizeReturn ? "void" : originalReturn;
                String parameterSection = shouldRemoveParameters ? "" : originalParameters.trim();
                String replacement = matcher.group(1)
                        + matcher.group(2)
                        + newReturn
                        + " "
                        + matcher.group(4)
                        + "(" + parameterSection + ")";
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement + " {"));
                modified = true;
            }
        }
        if (modified) {
            matcher.appendTail(buffer);
            context.setSourceCode(buffer.toString());
        }
    }

    private boolean containsValueReturn(String source, int bodyStart) {
        int depth = 1;
        for (int i = bodyStart; i < source.length() && depth > 0; i++) {
            char current = source.charAt(i);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                continue;
            }
            if (depth > 0 && current == 'r' && source.startsWith("return", i)) {
                int after = i + "return".length();
                if (after >= source.length()) {
                    continue;
                }
                while (after < source.length() && Character.isWhitespace(source.charAt(after))) {
                    after++;
                }
                if (after < source.length() && source.charAt(after) != ';') {
                    return true;
                }
            }
        }
        return false;
    }
}
