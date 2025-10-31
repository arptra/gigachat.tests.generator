package com.example.tests.generator.verification.rules;

import com.example.tests.generator.metadata.ClassMetadata;
import com.example.tests.generator.metadata.RelatedTypeMetadata;
import com.example.tests.generator.util.StandardLibraryTypeResolver;
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
        Map<String, String> canonicalImports = buildCanonicalImportIndex(metadataTypes, duplicateCheck);

        String rewrittenImports = rewriteExistingImports(source, canonicalImports);
        if (!rewrittenImports.equals(source)) {
            source = rewrittenImports;
            modified = true;
        }

        String cleanedImports = removeUnqualifiedImports(source);
        if (!cleanedImports.equals(source)) {
            source = cleanedImports;
            modified = true;
        }

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

        importsToAdd.addAll(detectMissingSimpleImports(source, canonicalImports));

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

    private Map<String, String> buildCanonicalImportIndex(Map<String, String> qualifiedToSimple,
                                                          Map<String, Long> duplicateCheck) {
        Map<String, String> simpleToQualified = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : qualifiedToSimple.entrySet()) {
            String simple = entry.getValue();
            if (duplicateCheck.getOrDefault(simple, 0L) > 1) {
                continue;
            }
            simpleToQualified.putIfAbsent(simple, entry.getKey());
        }
        StandardLibraryTypeResolver.aliases().forEach(simpleToQualified::putIfAbsent);
        return simpleToQualified;
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

    private String rewriteExistingImports(String source, Map<String, String> simpleToQualified) {
        if (simpleToQualified.isEmpty()) {
            return source;
        }
        Pattern importPattern = Pattern.compile("(?m)^\\s*import\\s+([^;]+)\\s*;\\s*$");
        Matcher matcher = importPattern.matcher(source);
        StringBuffer buffer = new StringBuffer();
        boolean modified = false;
        while (matcher.find()) {
            String current = matcher.group(1).trim();
            String canonical = canonicalImport(current, simpleToQualified);
            if (canonical == null) {
                matcher.appendReplacement(buffer, "");
                modified = true;
            } else if (!current.equals(canonical)) {
                matcher.appendReplacement(buffer, "import " + canonical + ";");
                modified = true;
            }
        }
        if (!modified) {
            return source;
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private Set<String> detectMissingSimpleImports(String source, Map<String, String> simpleToQualified) {
        Set<String> imports = new LinkedHashSet<>();
        if (simpleToQualified.isEmpty() || source == null || source.isBlank()) {
            return imports;
        }
        String body = stripHeader(source);
        for (Map.Entry<String, String> entry : simpleToQualified.entrySet()) {
            String simple = entry.getKey();
            String qualified = entry.getValue();
            if (simple == null || qualified == null || qualified.startsWith("java.lang.")) {
                continue;
            }
            if (!containsSimpleUsage(body, simple)) {
                continue;
            }
            if (hasImport(source, qualified)) {
                continue;
            }
            imports.add(qualified);
        }
        return imports;
    }

    private boolean hasImport(String source, String qualified) {
        Pattern pattern = Pattern.compile("(?m)^\\s*import\\s+" + Pattern.quote(qualified) + "\\s*;\\s*$");
        return pattern.matcher(source).find();
    }

    private boolean containsSimpleUsage(String body, String simple) {
        if (simple.isBlank()) {
            return false;
        }
        Pattern pattern = Pattern.compile("(?<!\\.)\\b" + Pattern.quote(simple) + "\\b");
        return pattern.matcher(body).find();
    }

    private String stripHeader(String source) {
        Pattern header = Pattern.compile("(?m)^\\s*(?:package\\s+[^;]+;|import\\s+[^;]+;)\\s*");
        Matcher matcher = header.matcher(source);
        return matcher.replaceAll("");
    }

    private String removeUnqualifiedImports(String source) {
        Pattern malformedImport = Pattern.compile("(?m)^\\s*import\\s+([\\w\\$]+)\\s*;\\s*$");
        Matcher matcher = malformedImport.matcher(source);
        if (!matcher.find()) {
            return source;
        }
        StringBuffer buffer = new StringBuffer();
        do {
            matcher.appendReplacement(buffer, "");
        } while (matcher.find());
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String canonicalImport(String declaredImport, Map<String, String> simpleToQualified) {
        if (declaredImport == null || declaredImport.isBlank()) {
            return declaredImport;
        }
        String simpleName = extractSimpleName(declaredImport);
        String candidate = simpleToQualified.get(simpleName);
        if (candidate != null) {
            return candidate;
        }
        if (declaredImport.contains(".")) {
            return declaredImport;
        }
        return null;
    }

}
