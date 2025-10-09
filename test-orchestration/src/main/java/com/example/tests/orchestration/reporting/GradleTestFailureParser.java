package com.example.tests.orchestration.reporting;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heuristically parses Gradle test output to extract failing test cases.
 */
public final class GradleTestFailureParser {

    private static final Pattern SUMMARY_LINE = Pattern.compile("^(\\S+) > (.+) FAILED$");
    private static final Pattern ANSI_ESCAPE = Pattern.compile("\u001B\\[[;\\d]*[@-~]");

    public List<TestFailureDetail> parse(String output) {
        List<TestFailureDetail> failures = new ArrayList<>();
        if (output == null || output.isBlank()) {
            return failures;
        }

        String sanitized = stripAnsi(output);
        String[] lines = sanitized.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("> Task")) {
                continue;
            }
            Matcher matcher = SUMMARY_LINE.matcher(trimmed);
            if (!matcher.matches()) {
                continue;
            }
            String testClass = matcher.group(1);
            String method = matcher.group(2);

            int j = i + 1;
            List<String> diagnostics = new ArrayList<>();
            String message = "";
            while (j < lines.length) {
                String followUp = lines[j];
                String followUpTrimmed = followUp.stripLeading();
                Matcher next = SUMMARY_LINE.matcher(followUpTrimmed);
                if (next.matches() || followUpTrimmed.startsWith("> Task")) {
                    break;
                }
                diagnostics.add(followUp);
                if (message.isEmpty() && !followUp.isBlank()) {
                    message = followUp.trim();
                }
                j++;
            }
            if (message.isEmpty()) {
                message = "Test failed";
            }
            failures.add(new TestFailureDetail(testClass, method, message, diagnostics));
            i = j - 1;
        }

        return failures;
    }

    private static String stripAnsi(String value) {
        return ANSI_ESCAPE.matcher(value).replaceAll("");
    }
}
