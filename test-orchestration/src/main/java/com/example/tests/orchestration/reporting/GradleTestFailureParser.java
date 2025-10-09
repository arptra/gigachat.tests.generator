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

    public List<TestFailureDetail> parse(String output) {
        List<TestFailureDetail> failures = new ArrayList<>();
        if (output == null || output.isBlank()) {
            return failures;
        }

        String[] lines = output.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("> Task")) {
                continue;
            }
            Matcher matcher = SUMMARY_LINE.matcher(line);
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
                Matcher next = SUMMARY_LINE.matcher(followUp);
                if (next.matches() || followUp.stripLeading().startsWith("> Task")) {
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
}
