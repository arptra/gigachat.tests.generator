package com.example.tests.orchestration.reporting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heuristically parses Gradle test output to extract failing test cases.
 */
public final class GradleTestFailureParser {

    private static final Pattern SUMMARY_LINE = Pattern.compile("^(\\S+) > (.+) FAILED$");
    private static final Pattern ANSI_ESCAPE = Pattern.compile("\u001B\\[[;\\d]*[@-~]");
    private static final Pattern COMPILATION_ERROR_LINE = Pattern.compile("^(.+?\\.java):(\\d+): error: (.+)$");
    private static final Pattern MOCKITO_ERROR_HEADER = Pattern.compile(
            "^(?:Caused by: )?org\\.mockito\\.(?:exceptions\\.|internal\\.|MockitoException).*$");
    private static final Pattern STACK_TRACE_LINE = Pattern.compile(
            "^(?:->\\s+)?at\\s+([\\w.$]+)\\.([\\w$<>]+)\\(([^:()]+)(?::(\\d+))?\\)$");
    private static final Pattern JAVA_FILE_REFERENCE = Pattern.compile("([\\w./-]+\\.java)");
    private static final Pattern TEST_RESULT_SUMMARY = Pattern.compile("^\\d+ tests? completed.*$");
    private static final Pattern TEST_CLASS_MENTION = Pattern.compile("([\\w.$]+(?:Test|Tests|IT))");
    private static final Pattern REPORT_LINK = Pattern.compile(
            "There were failing tests\\. See the report at: (\\S+)");

    private final MockitoReportParser reportParser;

    public GradleTestFailureParser() {
        this(new MockitoReportParser());
    }

    GradleTestFailureParser(MockitoReportParser reportParser) {
        this.reportParser = Objects.requireNonNull(reportParser, "reportParser");
    }

    public List<TestFailureDetail> parse(String output) {
        List<TestFailureDetail> failures = new ArrayList<>();
        if (output == null || output.isBlank()) {
            return failures;
        }

        String sanitized = stripAnsi(output);
        String[] lines = sanitized.split("\\R");
        String lastKnownTestClass = null;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("> Task")) {
                continue;
            }
            Matcher reportLink = REPORT_LINK.matcher(trimmed);
            if (reportLink.find()) {
                List<TestFailureDetail> reportFailures = reportParser.parseReport(reportLink.group(1));
                failures.addAll(reportFailures);
                if (!reportFailures.isEmpty()) {
                    lastKnownTestClass = reportFailures.get(reportFailures.size() - 1).getTestClass();
                }
                continue;
            }
            Matcher matcher = SUMMARY_LINE.matcher(trimmed);
            if (!matcher.matches()) {
                Matcher compilationMatcher = COMPILATION_ERROR_LINE.matcher(trimmed);
                if (compilationMatcher.matches()) {
                    TestFailureDetail detail = parseCompilationError(lines, i, compilationMatcher);
                    failures.add(detail);
                    lastKnownTestClass = detail.getTestClass();
                    i = advancePastCompilationError(lines, i + 1);
                    continue;
                }
                if (isMockitoErrorHeader(trimmed)) {
                    MockitoParseResult parsed = parseMockitoFailure(lines, i, lastKnownTestClass);
                    if (parsed != null) {
                        failures.add(parsed.detail());
                        lastKnownTestClass = parsed.detail().getTestClass();
                        i = parsed.endIndex();
                    }
                    continue;
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
            lastKnownTestClass = testClass;
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

    private MockitoParseResult parseMockitoFailure(String[] lines, int startIndex, String fallbackClass) {
        List<String> diagnostics = new ArrayList<>();
        int i = startIndex;
        while (i < lines.length) {
            String current = lines[i];
            String trimmed = current.stripLeading();
            if (i > startIndex) {
                if (SUMMARY_LINE.matcher(trimmed).matches()
                        || trimmed.startsWith("> Task")
                        || COMPILATION_ERROR_LINE.matcher(trimmed).matches()) {
                    break;
                }
                if (trimmed.startsWith("FAILURE: ") || TEST_RESULT_SUMMARY.matcher(trimmed).matches()) {
                    diagnostics.add(current);
                    i++;
                    break;
                }
            }
            diagnostics.add(current);
            i++;
        }

        if (diagnostics.isEmpty()) {
            return null;
        }

        String message = extractMockitoMessage(diagnostics);
        FailureDescriptor descriptor = inferFailureDescriptor(diagnostics, fallbackClass);
        if (descriptor.testClass == null) {
            return null;
        }

        TestFailureDetail detail = new TestFailureDetail(
                descriptor.testClass,
                descriptor.testMethod,
                message,
                diagnostics);
        return new MockitoParseResult(detail, i - 1);
    }

    private FailureDescriptor inferFailureDescriptor(List<String> diagnostics, String fallbackClass) {
        FailureDescriptor descriptor = new FailureDescriptor();
        for (String line : diagnostics) {
            String normalized = line.stripLeading();
            if (normalized.startsWith("->")) {
                normalized = normalized.substring(2).stripLeading();
            }
            Matcher stackMatcher = STACK_TRACE_LINE.matcher(normalized);
            if (stackMatcher.matches()) {
                String candidateClass = stackMatcher.group(1);
                String candidateMethod = stackMatcher.group(2);
                if (descriptor.testClass == null || looksLikeTestClass(candidateClass)) {
                    descriptor.testClass = candidateClass;
                    descriptor.testMethod = normalizeMethodName(candidateMethod);
                    if (looksLikeTestClass(candidateClass)) {
                        break;
                    }
                }
            }
        }

        if (descriptor.testClass == null) {
            for (String line : diagnostics) {
                Matcher fileMatcher = JAVA_FILE_REFERENCE.matcher(line);
                if (fileMatcher.find()) {
                    descriptor.testClass = resolveClassName(fileMatcher.group(1));
                    break;
                }
            }
        }

        if (descriptor.testClass == null) {
            for (String line : diagnostics) {
                Matcher mention = TEST_CLASS_MENTION.matcher(line);
                if (mention.find()) {
                    descriptor.testClass = mention.group(1);
                    break;
                }
            }
        }

        if (descriptor.testClass == null) {
            descriptor.testClass = fallbackClass;
        }

        if (descriptor.testMethod == null) {
            descriptor.testMethod = "unknownMockitoFailure";
        }

        return descriptor;
    }

    private String extractMockitoMessage(List<String> diagnostics) {
        String message = null;
        Integer messageIndex = null;
        for (int i = 0; i < diagnostics.size(); i++) {
            String line = diagnostics.get(i);
            if (!line.isBlank()) {
                message = line.trim();
                messageIndex = i;
                break;
            }
        }
        if (message == null || message.isEmpty()) {
            return "Mockito failure";
        }
        if (message.endsWith(":")) {
            for (int i = messageIndex + 1; i < diagnostics.size(); i++) {
                String line = diagnostics.get(i).trim();
                if (!line.isEmpty()) {
                    message = message + " " + line;
                    break;
                }
            }
        }
        return message;
    }

    private boolean looksLikeTestClass(String className) {
        if (className == null || className.isBlank()) {
            return false;
        }
        String simple = className;
        int lastDot = className.lastIndexOf('.');
        if (lastDot >= 0) {
            simple = className.substring(lastDot + 1);
        }
        return simple.endsWith("Test") || simple.endsWith("Tests") || simple.endsWith("IT");
    }

    private String normalizeMethodName(String method) {
        if (method == null || method.isBlank()) {
            return "unknownMockitoFailure";
        }
        if ("<init>".equals(method)) {
            return "initializationError";
        }
        if ("<clinit>".equals(method)) {
            return "classInitialization";
        }
        if (method.contains("(")) {
            return method;
        }
        if (method.endsWith("()")) {
            return method;
        }
        return method + "()";
    }

    private boolean isMockitoErrorHeader(String trimmedLine) {
        return MOCKITO_ERROR_HEADER.matcher(trimmedLine).matches();
    }

    private String resolveClassName(String sourcePath) {
        String normalized = sourcePath.replace('\\', '/');
        int javaRoot = normalized.indexOf("/src/");
        if (javaRoot >= 0) {
            int javaDir = normalized.indexOf("/java/", javaRoot);
            if (javaDir >= 0) {
                int start = javaDir + 6;
                if (start < normalized.length()) {
                    String relative = normalized.substring(start);
                    if (relative.endsWith(".java")) {
                        relative = relative.substring(0, relative.length() - 5);
                    }
                    return relative.replace('/', '.');
                }
            } else {
                int start = normalized.indexOf('/', javaRoot + 5);
                if (start >= 0 && start + 1 < normalized.length()) {
                    String relative = normalized.substring(start + 1);
                    if (relative.endsWith(".java")) {
                        relative = relative.substring(0, relative.length() - 5);
                    }
                    return relative.replace('/', '.');
                }
            }
        }

        int lastSlash = normalized.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        if (fileName.endsWith(".java")) {
            fileName = fileName.substring(0, fileName.length() - 5);
        }
        return fileName;
    }

    private static final class FailureDescriptor {
        private String testClass;
        private String testMethod;
    }

    private static final class MockitoParseResult {
        private final TestFailureDetail detail;
        private final int endIndex;

        private MockitoParseResult(TestFailureDetail detail, int endIndex) {
            this.detail = detail;
            this.endIndex = endIndex;
        }

        TestFailureDetail detail() {
            return detail;
        }

        int endIndex() {
            return endIndex;
        }
    }
}
