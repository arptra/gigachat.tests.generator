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
    private static final Pattern COMPILATION_ERROR_LINE = Pattern.compile("^(.+?\\.java):(\\d+): error: (.+)$");

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
                Matcher compilationMatcher = COMPILATION_ERROR_LINE.matcher(trimmed);
                if (compilationMatcher.matches()) {
                    failures.add(parseCompilationError(lines, i, compilationMatcher));
                    i = advancePastCompilationError(lines, i + 1);
                }
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

    private TestFailureDetail parseCompilationError(String[] lines, int index, Matcher matcher) {
        String sourcePath = matcher.group(1);
        String message = matcher.group(3).trim();
        List<String> diagnostics = new ArrayList<>();
        diagnostics.add(lines[index]);

        int j = index + 1;
        while (j < lines.length) {
            String followUp = lines[j];
            String followUpTrimmed = followUp.stripLeading();
            if (SUMMARY_LINE.matcher(followUpTrimmed).matches()
                    || followUpTrimmed.startsWith("> Task")
                    || COMPILATION_ERROR_LINE.matcher(followUpTrimmed).matches()) {
                break;
            }
            diagnostics.add(followUp);
            j++;
        }

        return new TestFailureDetail(resolveClassName(sourcePath), "compileTestJava", message, diagnostics);
    }

    private int advancePastCompilationError(String[] lines, int index) {
        int i = index;
        while (i < lines.length) {
            String trimmed = lines[i].stripLeading();
            if (SUMMARY_LINE.matcher(trimmed).matches()
                    || trimmed.startsWith("> Task")
                    || COMPILATION_ERROR_LINE.matcher(trimmed).matches()) {
                break;
            }
            i++;
        }
        return i - 1;
    }

    private String resolveClassName(String sourcePath) {
        String normalized = sourcePath.replace('\\', '/');
        int javaRoot = normalized.indexOf("/src/");
        if (javaRoot >= 0) {
            int start = normalized.indexOf('/', javaRoot + 5);
            if (start >= 0 && start + 1 < normalized.length()) {
                String relative = normalized.substring(start + 1);
                if (relative.endsWith(".java")) {
                    relative = relative.substring(0, relative.length() - 5);
                }
                return relative.replace('/', '.');
            }
        }

        int lastSlash = normalized.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        if (fileName.endsWith(".java")) {
            fileName = fileName.substring(0, fileName.length() - 5);
        }
        return fileName;
    }
}
