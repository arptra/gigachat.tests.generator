package com.example.tests.orchestration.reporting;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Attempts to extract failing Mockito tests from the Gradle HTML report referenced in the
 * command line output.
 */
public final class MockitoReportParser {

    private static final Pattern REPORT_REFERENCE = Pattern.compile(
            "([\\w.$]+)\\s*>\\s*([\\w$<>]+)\\s+FAILED",
            Pattern.CASE_INSENSITIVE);

    public List<TestFailureDetail> parseReport(String location) {
        if (location == null || location.isBlank()) {
            return List.of();
        }
        Path path = resolvePath(location.trim());
        if (path == null) {
            return List.of();
        }
        return parseReport(path);
    }

    public List<TestFailureDetail> parseReport(Path reportPath) {
        Objects.requireNonNull(reportPath, "reportPath");
        if (!Files.exists(reportPath)) {
            return List.of();
        }
        String contents;
        try {
            contents = Files.readString(reportPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return List.of();
        }
        if (contents.isBlank()) {
            return List.of();
        }
        String normalized = decodeHtmlEntities(contents);
        Matcher matcher = REPORT_REFERENCE.matcher(normalized);
        List<TestFailureDetail> failures = new ArrayList<>();
        while (matcher.find()) {
            String testClass = matcher.group(1);
            String method = matcher.group(2);
            String message = "See Gradle HTML report for Mockito failure details";
            List<String> diagnostics = List.of("Report excerpt: " + matcher.group(0));
            failures.add(new TestFailureDetail(testClass, normalizeMethod(method), message, diagnostics));
        }
        return failures;
    }

    private Path resolvePath(String location) {
        try {
            if (location.startsWith("file:")) {
                URI uri = URI.create(location);
                return Paths.get(uri);
            }
            return Paths.get(location);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String decodeHtmlEntities(String text) {
        return text
                .replace("&gt;", ">")
                .replace("&lt;", "<")
                .replace("&amp;", "&");
    }

    private String normalizeMethod(String method) {
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
}
