package com.example.tests.generator.verification.rules;

import com.example.tests.generator.metadata.ClassMetadata;
import com.example.tests.generator.metadata.RelatedTypeMetadata;
import com.example.tests.generator.verification.GeneratedTestContext;
import com.example.tests.generator.verification.GeneratedTestRule;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Rewrites fully-qualified type usages into canonical imports and short names
 * whenever the type belongs to the analysed domain or to the Java standard
 * library. This keeps the generated test readable even when the language model
 * omits import statements in its response.
 */
public final class FullyQualifiedTypeImportRule implements GeneratedTestRule {

    private static final Pattern JAVA_TYPE_PATTERN = Pattern.compile("\\b(java\\.[a-zA-Z0-9_.]*\\.[A-Z][A-Za-z0-9_]*)\\b");

    @Override
    public void apply(GeneratedTestContext context) {
        Objects.requireNonNull(context, "context");
        String source = context.getSourceCode();
        boolean modified = false;
        Set<String> importsToAdd = new LinkedHashSet<>();

        Map<String, String> metadataTypes = collectMetadataTypes(context);
        Map<String, Long> duplicateCheck = metadataTypes.values().stream()
                .collect(Collectors.groupingBy(simple -> simple, LinkedHashMap::new, Collectors.counting()));
        for (Map.Entry<String, String> entry : metadataTypes.entrySet()) {
            String qualified = entry.getKey();
            String simple = entry.getValue();
            if (duplicateCheck.getOrDefault(simple, 0L) > 1) {
                continue;
            }
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(qualified) + "\\b");
            Matcher matcher = pattern.matcher(source);
            if (matcher.find()) {
                source = matcher.replaceAll(simple);
                importsToAdd.add(qualified);
                modified = true;
            }
        }

        Matcher javaMatcher = JAVA_TYPE_PATTERN.matcher(source);
        StringBuffer buffer = new StringBuffer();
        boolean javaModified = false;
        while (javaMatcher.find()) {
            String qualified = javaMatcher.group(1);
            if (qualified.startsWith("java.lang.")) {
                continue;
            }
            String simple = extractSimpleName(qualified);
            javaMatcher.appendReplacement(buffer, simple);
            importsToAdd.add(qualified);
            javaModified = true;
        }
        if (javaModified) {
            javaMatcher.appendTail(buffer);
            source = buffer.toString();
            modified = true;
        }

        if (!importsToAdd.isEmpty()) {
            for (String qualified : importsToAdd) {
                source = ensureImport(source, qualified);
            }
            modified = true;
        }

        String collapsed = collapseAssignmentNewlines(source);
        if (!collapsed.equals(source)) {
            source = collapsed;
            modified = true;
        }

        if (modified) {
            context.setSourceCode(source);
        }
    }

    private Map<String, String> collectMetadataTypes(GeneratedTestContext context) {
        Map<String, String> qualifiedToSimple = new LinkedHashMap<>();
        Optional<ClassMetadata> metadata = context.getMetadata();
        if (metadata.isEmpty()) {
            return qualifiedToSimple;
        }
        ClassMetadata owner = metadata.get();
        register(qualifiedToSimple, owner.getFullyQualifiedName());
        owner.getDependencies().forEach(dependency -> register(qualifiedToSimple, dependency));
        owner.getSupportingTypes().stream()
                .map(RelatedTypeMetadata::getQualifiedName)
                .forEach(type -> register(qualifiedToSimple, type));
        owner.getDependencyMethods().keySet().forEach(type -> register(qualifiedToSimple, type));
        owner.getSupportingTypeMembers().keySet().forEach(type -> register(qualifiedToSimple, type));
        return qualifiedToSimple;
    }

    private void register(Map<String, String> target, String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isBlank()) {
            return;
        }
        int lastDot = qualifiedName.lastIndexOf('.');
        if (lastDot <= 0 || lastDot >= qualifiedName.length() - 1) {
            return;
        }
        String simple = qualifiedName.substring(lastDot + 1);
        if (simple.isBlank()) {
            return;
        }
        target.putIfAbsent(qualifiedName, simple);
    }

    private String extractSimpleName(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot >= 0 && lastDot + 1 < qualifiedName.length()
                ? qualifiedName.substring(lastDot + 1)
                : qualifiedName;
    }

    private String ensureImport(String source, String qualified) {
        if (qualified == null || qualified.isBlank() || qualified.startsWith("java.lang.")) {
            return source;
        }
        Pattern existing = Pattern.compile("(?m)^\\s*import\\s+" + Pattern.quote(qualified) + "\\s*;\\s*$");
        if (existing.matcher(source).find()) {
            return source;
        }
        int packageEnd = locatePackageStatementEnd(source);
        StringBuilder builder = new StringBuilder();
        String importLine = String.format(Locale.ENGLISH, "import %s;\n", qualified);
        if (packageEnd > 0) {
            builder.append(source, 0, packageEnd);
            if (packageEnd > 0 && source.charAt(packageEnd - 1) != '\n') {
                builder.append('\n');
            }
            builder.append(importLine);
            if (packageEnd >= source.length() || source.charAt(packageEnd) != '\n') {
                builder.append('\n');
            }
            builder.append(source.substring(packageEnd));
        } else {
            builder.append(importLine).append('\n').append(source);
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

    private String collapseAssignmentNewlines(String source) {
        return source.replaceAll("=\\s*(?:\\r?\\n)\\s*new", "= new");
    }
}
