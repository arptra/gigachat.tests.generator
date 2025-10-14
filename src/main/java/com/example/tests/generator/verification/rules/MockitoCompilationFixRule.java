package com.example.tests.generator.verification.rules;

import com.example.tests.generator.verification.GeneratedTestContext;
import com.example.tests.generator.verification.GeneratedTestRule;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Attempts to fix the most common Mockito related compilation issues by adding
 * the required JUnit extension annotation and missing imports.
 */
public final class MockitoCompilationFixRule implements GeneratedTestRule {

    private static final Pattern CLASS_DECLARATION = Pattern.compile(
            "(?m)^(?:public|protected|private)?\\s*(?:final\\s+)?class\\s+\\w+[^\\{]*\\{");

    @Override
    public void apply(GeneratedTestContext context) {
        Objects.requireNonNull(context, "context");
        String source = context.getSourceCode();
        boolean usesMock = containsAny(source, "@Mock");
        boolean usesSpy = containsAny(source, "@Spy");
        boolean usesCaptor = containsAny(source, "@Captor");
        boolean usesInjectMocks = containsAny(source, "@InjectMocks");
        boolean usesMockito = containsAny(source, "Mockito.");
        boolean usesArgumentCaptor = containsAny(source, "ArgumentCaptor");
        boolean usesArgumentMatchers = containsAny(source, "ArgumentMatchers.");
        boolean usesBddMockito = containsAny(source, "BDDMockito.");
        boolean alreadyExtendWith = containsAny(source, "@ExtendWith(");
        boolean mentionsMockitoExtension = containsAny(source, "MockitoExtension");

        Set<String> importsToEnsure = new LinkedHashSet<>();
        if (usesMock) {
            importsToEnsure.add("org.mockito.Mock");
        }
        if (usesSpy) {
            importsToEnsure.add("org.mockito.Spy");
        }
        if (usesCaptor) {
            importsToEnsure.add("org.mockito.Captor");
        }
        if (usesInjectMocks) {
            importsToEnsure.add("org.mockito.InjectMocks");
        }
        if (usesMockito) {
            importsToEnsure.add("org.mockito.Mockito");
        }
        if (usesArgumentCaptor) {
            importsToEnsure.add("org.mockito.ArgumentCaptor");
        }
        if (usesArgumentMatchers) {
            importsToEnsure.add("org.mockito.ArgumentMatchers");
        }
        if (usesBddMockito) {
            importsToEnsure.add("org.mockito.BDDMockito");
        }

        boolean shouldAddExtension = (usesMock || usesSpy || usesInjectMocks || mentionsMockitoExtension)
                && !alreadyExtendWith;
        if (shouldAddExtension) {
            source = insertExtendWithAnnotation(source);
            importsToEnsure.add("org.junit.jupiter.api.extension.ExtendWith");
            importsToEnsure.add("org.mockito.junit.jupiter.MockitoExtension");
        } else if (alreadyExtendWith && containsAny(source, "MockitoExtension") ) {
            importsToEnsure.add("org.mockito.junit.jupiter.MockitoExtension");
        }

        if (!importsToEnsure.isEmpty()) {
            source = ensureImportsPresent(source, importsToEnsure);
        }

        context.setSourceCode(source);
    }

    private boolean containsAny(String source, String needle) {
        return source.contains(needle);
    }

    private String insertExtendWithAnnotation(String source) {
        Matcher matcher = CLASS_DECLARATION.matcher(source);
        if (!matcher.find()) {
            return source;
        }
        int lineStart = matcher.start();
        while (lineStart > 0 && source.charAt(lineStart - 1) != '\n' && source.charAt(lineStart - 1) != '\r') {
            lineStart--;
        }
        int indentEnd = lineStart;
        while (indentEnd < source.length() && (source.charAt(indentEnd) == ' ' || source.charAt(indentEnd) == '\t')) {
            indentEnd++;
        }
        String indent = source.substring(lineStart, indentEnd);
        StringBuilder builder = new StringBuilder();
        builder.append(source, 0, lineStart);
        if (lineStart > 0 && source.charAt(lineStart - 1) != '\n') {
            builder.append('\n');
        }
        builder.append(indent).append("@ExtendWith(MockitoExtension.class)").append('\n');
        builder.append(source.substring(lineStart));
        return builder.toString();
    }

    private String ensureImportsPresent(String source, Set<String> importsToEnsure) {
        Pattern importPattern = Pattern.compile("(?m)^\\s*import\\s+([\\w\\.]+(?:\\.\\*)?)\\s*;\\s*$");
        Matcher matcher = importPattern.matcher(source);
        Set<String> existing = new LinkedHashSet<>();
        int lastImportEnd = -1;
        while (matcher.find()) {
            lastImportEnd = matcher.end();
            existing.add(matcher.group(1));
        }

        List<String> missing = importsToEnsure.stream()
                .filter(importName -> existing.stream().noneMatch(current -> current.equals(importName)))
                .collect(Collectors.toList());
        if (missing.isEmpty()) {
            return source;
        }

        String importBlock = missing.stream()
                .map(name -> "import " + name + ";\n")
                .collect(Collectors.joining());

        StringBuilder builder = new StringBuilder();
        if (lastImportEnd != -1) {
            builder.append(source, 0, lastImportEnd);
            builder.append(importBlock);
            builder.append(source.substring(lastImportEnd));
        } else {
            int packageEnd = locatePackageStatementEnd(source);
            builder.append(source, 0, packageEnd);
            if (packageEnd > 0 && source.charAt(packageEnd - 1) != '\n') {
                builder.append('\n');
            }
            builder.append(importBlock);
            builder.append('\n');
            builder.append(source.substring(packageEnd));
        }
        return builder.toString();
    }

    private int locatePackageStatementEnd(String source) {
        Pattern packagePattern = Pattern.compile("(?m)^\\s*package\\s+[\\w\\.]+\\s*;\\s*$");
        Matcher matcher = packagePattern.matcher(source);
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
