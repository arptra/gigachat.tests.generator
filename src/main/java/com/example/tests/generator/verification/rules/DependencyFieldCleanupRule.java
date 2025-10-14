package com.example.tests.generator.verification.rules;

import com.example.tests.generator.verification.GeneratedTestContext;
import com.example.tests.generator.verification.GeneratedTestRule;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes Mockito injected fields that are declared but never used inside the
 * generated test class.
 */
public final class DependencyFieldCleanupRule implements GeneratedTestRule {

    private static final Pattern MOCK_FIELD_PATTERN = Pattern.compile(
            "(?ms)(\s*@(?:Mock|Spy|Captor)\\s*(?:@\\w+[^\\r\\n]*\\s*)*(?:private|protected|public)\\s+[\\w<>\\[\\],\\s]+?\\s+(\\w+)\\s*;\\s*)");

    @Override
    public void apply(GeneratedTestContext context) {
        Objects.requireNonNull(context, "context");
        String source = context.getSourceCode();
        boolean modified;
        do {
            modified = false;
            Matcher matcher = MOCK_FIELD_PATTERN.matcher(source);
            while (matcher.find()) {
                String fieldName = matcher.group(2);
                if (!isFieldUsed(source, fieldName, matcher.start(), matcher.end())) {
                    int removalStart = adjustRemovalStart(source, matcher.start());
                    int removalEnd = adjustRemovalEnd(source, matcher.end());
                    source = source.substring(0, removalStart) + source.substring(removalEnd);
                    source = collapseBlankLines(source);
                    modified = true;
                    break;
                }
            }
        } while (modified);
        context.setSourceCode(source);
    }

    private boolean isFieldUsed(String source, String fieldName, int declarationStart, int declarationEnd) {
        Pattern usagePattern = Pattern.compile("\\b" + Pattern.quote(fieldName) + "\\b");
        Matcher usageMatcher = usagePattern.matcher(source);
        while (usageMatcher.find()) {
            int start = usageMatcher.start();
            if (start >= declarationStart && start < declarationEnd) {
                continue;
            }
            return true;
        }
        return false;
    }

    private int adjustRemovalStart(String source, int start) {
        int removalStart = start;
        while (removalStart > 0 && source.charAt(removalStart - 1) == ' ') {
            removalStart--;
        }
        if (removalStart > 0 && (source.charAt(removalStart - 1) == '\n' || source.charAt(removalStart - 1) == '\r')) {
            removalStart--;
            if (removalStart > 0 && source.charAt(removalStart - 1) == '\r') {
                removalStart--;
            }
        }
        return removalStart;
    }

    private int adjustRemovalEnd(String source, int end) {
        int removalEnd = end;
        while (removalEnd < source.length() && (source.charAt(removalEnd) == '\t' || source.charAt(removalEnd) == ' ')) {
            removalEnd++;
        }
        if (removalEnd < source.length() && (source.charAt(removalEnd) == '\n' || source.charAt(removalEnd) == '\r')) {
            removalEnd++;
            if (removalEnd < source.length() && source.charAt(removalEnd) == '\n') {
                removalEnd++;
            }
        }
        return removalEnd;
    }

    private String collapseBlankLines(String source) {
        return source.replaceAll("(?m)\n{3,}", "\n\n");
    }
}
