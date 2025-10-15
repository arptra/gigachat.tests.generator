package com.example.tests.generator.verification.rules;

import com.example.tests.generator.metadata.ClassMetadata;
import com.example.tests.generator.metadata.MethodMetadata;
import com.example.tests.generator.metadata.RelatedTypeMetadata;
import com.example.tests.generator.verification.GeneratedTestContext;
import com.example.tests.generator.verification.GeneratedTestRule;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aligns variable declarations with the actual return types of the class under test
 * when compilation diagnostics report incompatible assignment types.
 */
public final class ReturnTypeCorrectionRule implements GeneratedTestRule {

    private static final Pattern INCOMPATIBLE_TYPES = Pattern.compile("incompatible types: (.+) cannot be converted to (.+)");
    private static final Pattern CAST_PATTERN = Pattern.compile("\\(\\s*([\\w\\.<>,\\[\\]]+)\\s*\\)");
    private static final Set<String> JAVA_LANG_TYPES = Set.of(
            "String", "Integer", "Long", "Double", "Float", "Boolean",
            "Short", "Byte", "Character", "Object", "Void"
    );

    @Override
    public void apply(GeneratedTestContext context) {
        Objects.requireNonNull(context, "context");
        if (context.getCompilationErrors().isEmpty()) {
            return;
        }
        Optional<ClassMetadata> metadataOptional = context.getMetadata();
        if (metadataOptional.isEmpty()) {
            return;
        }
        Map<String, String> mismatches = parseIncompatibleTypeErrors(context.getCompilationErrors());
        if (mismatches.isEmpty()) {
            return;
        }
        ClassMetadata metadata = metadataOptional.get();
        String source = context.getSourceCode();
        boolean modified = false;
        for (MethodMetadata method : metadata.getMethods()) {
            String resolvedReturnType = resolveTypeName(method.getReturnType(), metadata);
            String actualSimple = simpleName(resolvedReturnType);
            for (Map.Entry<String, String> entry : mismatches.entrySet()) {
                if (!actualSimple.equals(entry.getValue())) {
                    continue;
                }
                ReplacementResult replacement = replaceAssignments(source, entry.getKey(), resolvedReturnType, method.getName());
                if (replacement.modified()) {
                    source = replacement.source();
                    modified = true;
                }
            }
        }
        if (modified) {
            context.setSourceCode(source);
        }
    }

    private Map<String, String> parseIncompatibleTypeErrors(List<String> compilationErrors) {
        Map<String, String> mismatches = new LinkedHashMap<>();
        for (String error : compilationErrors) {
            Matcher matcher = INCOMPATIBLE_TYPES.matcher(error);
            if (matcher.find()) {
                String actual = simpleName(matcher.group(1));
                String expected = simpleName(matcher.group(2));
                if (!actual.isEmpty() && !expected.isEmpty() && !actual.equals(expected)) {
                    mismatches.put(expected, actual);
                }
            }
        }
        return mismatches;
    }

    private ReplacementResult replaceAssignments(String source,
                                                 String expectedSimple,
                                                 String resolvedActualType,
                                                 String methodName) {
        Pattern assignmentPattern = Pattern.compile(
                "(?m)^(?<indent>[ \\t]*)(?<mods>(?:final\\s+)*)" +
                        "(?<declType>[\\w\\.<>,\\[\\]]+)\\s+(?<name>\\w+)\\s*=\\s*(?<expr>[^;]*\\." +
                        Pattern.quote(methodName) + "\\s*\\([^;]*\\))\\s*;",
                Pattern.MULTILINE | Pattern.DOTALL);
        Matcher matcher = assignmentPattern.matcher(source);
        StringBuffer buffer = new StringBuffer();
        boolean replaced = false;
        while (matcher.find()) {
            String declaredType = matcher.group("declType").trim();
            if (!simpleName(declaredType).equals(expectedSimple)) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            String indent = matcher.group("indent");
            String modifiers = matcher.group("mods");
            if (modifiers == null) {
                modifiers = "";
            }
            String expression = stripMatchingCast(matcher.group("expr"), expectedSimple);
            String replacement = indent + modifiers + resolvedActualType + " " + matcher.group("name") + " = " + expression + ";";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
            replaced = true;
        }
        matcher.appendTail(buffer);
        if (replaced) {
            return new ReplacementResult(buffer.toString(), true);
        }
        return new ReplacementResult(source, false);
    }

    private String stripMatchingCast(String expression, String expectedSimple) {
        String trimmed = expression.trim();
        Matcher matcher = CAST_PATTERN.matcher(trimmed);
        while (matcher.find()) {
            if (!isCastAtExpressionStart(trimmed, matcher.start())) {
                continue;
            }
            String castType = matcher.group(1).trim();
            if (simpleName(castType).equals(expectedSimple)) {
                String before = trimmed.substring(0, matcher.start());
                String after = trimmed.substring(matcher.end());
                trimmed = (before + after).trim();
                matcher = CAST_PATTERN.matcher(trimmed);
                continue;
            }
        }
        return trimmed;
    }

    private boolean isCastAtExpressionStart(String expression, int index) {
        for (int i = 0; i < index; i++) {
            char current = expression.charAt(i);
            if (!Character.isWhitespace(current) && current != '(') {
                return false;
            }
        }
        return true;
    }

    private String resolveTypeName(String rawType, ClassMetadata metadata) {
        if (rawType == null || rawType.isBlank()) {
            return rawType;
        }
        String type = rawType.trim();
        String generics = "";
        int genericIndex = type.indexOf('<');
        if (genericIndex >= 0) {
            generics = type.substring(genericIndex);
            type = type.substring(0, genericIndex);
        }
        String arraySuffix = "";
        while (type.endsWith("[]")) {
            arraySuffix += "[]";
            type = type.substring(0, type.length() - 2);
        }
        String resolvedBase = resolveSimpleType(type, metadata);
        return resolvedBase + generics + arraySuffix;
    }

    private String resolveSimpleType(String baseType, ClassMetadata metadata) {
        String candidate = baseType.trim();
        if (candidate.isEmpty() || "void".equals(candidate)) {
            return candidate;
        }
        if (candidate.contains(".")) {
            return candidate;
        }
        if (isPrimitive(candidate) || JAVA_LANG_TYPES.contains(candidate)) {
            return candidate;
        }
        if (metadata != null) {
            if (candidate.equals(metadata.getClassName())) {
                return metadata.getFullyQualifiedName();
            }
            for (RelatedTypeMetadata supporting : metadata.getSupportingTypes()) {
                if (candidate.equals(supporting.getClassName())
                        || candidate.equals(simpleName(supporting.getQualifiedName()))) {
                    return supporting.getQualifiedName();
                }
            }
            return metadata.getFullyQualifiedName() + "." + candidate;
        }
        return candidate;
    }

    private boolean isPrimitive(String candidate) {
        return switch (candidate) {
            case "byte", "short", "int", "long", "float", "double", "boolean", "char" -> true;
            default -> false;
        };
    }

    private String simpleName(String typeName) {
        if (typeName == null) {
            return "";
        }
        String trimmed = typeName.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        int genericIndex = trimmed.indexOf('<');
        if (genericIndex >= 0) {
            trimmed = trimmed.substring(0, genericIndex);
        }
        while (trimmed.endsWith("[]")) {
            trimmed = trimmed.substring(0, trimmed.length() - 2);
        }
        int lastDot = trimmed.lastIndexOf('.');
        if (lastDot >= 0) {
            trimmed = trimmed.substring(lastDot + 1);
        }
        return trimmed.replace('$', '.').trim();
    }

    private record ReplacementResult(String source, boolean modified) {
    }
}
