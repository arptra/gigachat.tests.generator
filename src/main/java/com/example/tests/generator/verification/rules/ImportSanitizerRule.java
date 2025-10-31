package com.example.tests.generator.verification.rules;

import com.example.tests.generator.verification.GeneratedTestContext;
import com.example.tests.generator.verification.GeneratedTestRule;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Ensures that required JUnit and Mockito imports are present and removes simple
 * duplicates while keeping the original import order.
 */
public final class ImportSanitizerRule implements GeneratedTestRule {

    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "(?m)^\\s*import\\s+(static\\s+)?([\\w\\.]+(?:\\.\\*)?)\\s*;\\s*$");

    @Override
    public void apply(GeneratedTestContext context) {
        Objects.requireNonNull(context, "context");
        String source = context.getSourceCode();
        Matcher matcher = IMPORT_PATTERN.matcher(source);
        Set<String> orderedImports = new LinkedHashSet<>();
        int firstImportStart = -1;
        int lastImportEnd = -1;
        while (matcher.find()) {
            if (firstImportStart == -1) {
                firstImportStart = matcher.start();
            }
            lastImportEnd = matcher.end();
            String prefix = matcher.group(1) == null ? "" : "static ";
            String importTarget = matcher.group(2);
            if (!isQualifiedImport(importTarget)) {
                continue;
            }
            orderedImports.add(prefix + importTarget);
        }

        Set<String> requiredImports = collectRequiredImports(source);
        requiredImports.stream()
                .filter(required -> orderedImports.stream().noneMatch(existing -> existing.equals(required)))
                .forEach(orderedImports::add);

        if (firstImportStart == -1) {
            if (orderedImports.isEmpty()) {
                return;
            }
            String importBlock = orderedImports.stream()
                    .filter(entry -> !entry.startsWith("static "))
                    .map(entry -> "import " + entry + ";\n")
                    .collect(Collectors.joining());
            String staticBlock = orderedImports.stream()
                    .filter(entry -> entry.startsWith("static "))
                    .map(entry -> "import " + entry + ";\n")
                    .collect(Collectors.joining());
            String block = importBlock + staticBlock;
            int packageEnd = locatePackageStatementEnd(source);
            StringBuilder builder = new StringBuilder();
            builder.append(source, 0, packageEnd);
            if (packageEnd > 0 && source.charAt(packageEnd - 1) != '\n') {
                builder.append('\n');
            }
            builder.append(block);
            if (!block.isEmpty() && (packageEnd >= source.length() || source.charAt(packageEnd) != '\n')) {
                builder.append('\n');
            }
            builder.append(source.substring(packageEnd));
            context.setSourceCode(builder.toString());
            return;
        }

        StringBuilder builder = new StringBuilder();
        builder.append(source, 0, firstImportStart);
        String normalBlock = orderedImports.stream()
                .filter(entry -> !entry.startsWith("static "))
                .map(entry -> "import " + entry + ";\n")
                .collect(Collectors.joining());
        String staticBlock = orderedImports.stream()
                .filter(entry -> entry.startsWith("static "))
                .map(entry -> "import " + entry + ";\n")
                .collect(Collectors.joining());
        builder.append(normalBlock).append(staticBlock);
        if (!normalBlock.isEmpty() || !staticBlock.isEmpty()) {
            builder.append('\n');
        }
        builder.append(source.substring(lastImportEnd));
        context.setSourceCode(builder.toString());
    }

    private Set<String> collectRequiredImports(String source) {
        Set<String> required = new LinkedHashSet<>();
        if (source.contains("@Test")) {
            required.add("org.junit.jupiter.api.Test");
        }
        if (source.contains("@BeforeEach")) {
            required.add("org.junit.jupiter.api.BeforeEach");
        }
        if (source.contains("@BeforeAll")) {
            required.add("org.junit.jupiter.api.BeforeAll");
        }
        if (source.contains("@AfterEach")) {
            required.add("org.junit.jupiter.api.AfterEach");
        }
        if (source.contains("@AfterAll")) {
            required.add("org.junit.jupiter.api.AfterAll");
        }
        if (source.contains("@ParameterizedTest")) {
            required.add("org.junit.jupiter.params.ParameterizedTest");
        }
        if (source.contains("@CsvSource")) {
            required.add("org.junit.jupiter.params.provider.CsvSource");
        }
        if (source.contains("@MethodSource")) {
            required.add("org.junit.jupiter.params.provider.MethodSource");
        }
        if (source.contains("@ExtendWith")) {
            required.add("org.junit.jupiter.api.extension.ExtendWith");
        }
        if (source.contains("Assertions.")) {
            required.add("org.junit.jupiter.api.Assertions");
        }
        if (source.contains("Assumptions.")) {
            required.add("org.junit.jupiter.api.Assumptions");
        }
        if (source.contains("MockitoExtension")) {
            required.add("org.mockito.junit.jupiter.MockitoExtension");
        }
        if (source.contains("ArgumentCaptor")) {
            required.add("org.mockito.ArgumentCaptor");
        }
        if (source.contains("Mockito.")) {
            required.add("org.mockito.Mockito");
        }
        if (source.contains("ArgumentMatchers.")) {
            required.add("org.mockito.ArgumentMatchers");
        }
        if (source.contains("BDDMockito.")) {
            required.add("org.mockito.BDDMockito");
        }
        if (source.contains("@Mock")) {
            required.add("org.mockito.Mock");
        }
        if (source.contains("@Spy")) {
            required.add("org.mockito.Spy");
        }
        if (source.contains("@Captor")) {
            required.add("org.mockito.Captor");
        }
        if (source.contains("@InjectMocks")) {
            required.add("org.mockito.InjectMocks");
        }
        return required;
    }

    private boolean isQualifiedImport(String importTarget) {
        if (importTarget == null || importTarget.isBlank()) {
            return false;
        }
        int dotIndex = importTarget.indexOf('.');
        return dotIndex > 0;
    }

    private int locatePackageStatementEnd(String source) {
        Matcher matcher = Pattern.compile("(?m)^\\s*package\\s+[\\w\\.]+\\s*;\\s*$").matcher(source);
        if (matcher.find()) {
            int end = matcher.end();
            while (end < source.length() && (source.charAt(end) == '\r' || source.charAt(end) == '\n')) {
                end++;
            }
            return end;
        }
        return 0;
    }
}
