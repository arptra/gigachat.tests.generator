package com.example.tests.generator.analysis;

import com.example.tests.generator.metadata.ClassMetadata;
import com.example.tests.generator.metadata.MetadataTransformer;
import com.example.tests.generator.metadata.MethodMetadata;
import com.example.tests.generator.metadata.RelatedTypeMetadata;
import com.example.tests.generator.model.ClassKind;
import com.example.tests.orchestration.analysis.DependencyNode;
import com.example.tests.orchestration.analysis.InvocationArgument;
import com.example.tests.orchestration.analysis.MethodDependencyAnalyzer;
import com.example.tests.orchestration.analysis.MethodDependencyGraph;
import com.example.tests.orchestration.analysis.MethodInvocation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Builds documentation about the dependencies observed within test targets so
 * the LLM prompt can include precise constructor and method signatures.
 */
public final class TestDependencyDocumentationBuilder {

    private final MethodDependencyAnalyzer analyzer;
    private final MetadataTransformer metadataTransformer;

    public TestDependencyDocumentationBuilder(MethodDependencyAnalyzer analyzer,
                                              MetadataTransformer metadataTransformer) {
        this.analyzer = Objects.requireNonNull(analyzer, "analyzer");
        this.metadataTransformer = Objects.requireNonNull(metadataTransformer, "metadataTransformer");
    }

    public Map<String, List<String>> buildDocumentation(ClassMetadata promptMetadata,
                                                        com.example.tests.generator.model.ClassMetadata ownerMetadata,
                                                        String testSource) {
        Map<String, List<String>> documentation = new LinkedHashMap<>();
        Map<String, ClassMetadata> promptCache = new LinkedHashMap<>();

        if (promptMetadata != null) {
            promptCache.put(promptMetadata.getFullyQualifiedName(), promptMetadata);
            seedSupportingTypes(documentation, promptMetadata);
        }

        if (ownerMetadata == null) {
            return documentation;
        }

        Deque<PendingMethod> queue = new ArrayDeque<>();
        Set<String> visited = new LinkedHashSet<>();

        selectMethodNames(ownerMetadata, promptMetadata == null ? null : promptMetadata.getClassName(), testSource)
                .forEach(name -> queue.addLast(new PendingMethod(ownerMetadata, name)));

        if (queue.isEmpty()) {
            ownerMetadata.getMethods().forEach(method -> queue.addLast(new PendingMethod(ownerMetadata, method.getName())));
        }

        while (!queue.isEmpty()) {
            PendingMethod pending = queue.removeFirst();
            if (!visited.add(pending.key())) {
                continue;
            }
            MethodDependencyGraph graph;
            try {
                graph = analyzer.analyze(pending.owner().getSourcePath(),
                        pending.owner().getQualifiedName(), pending.methodName());
            } catch (RuntimeException ex) {
                continue;
            }
            processGraph(pending.owner(), graph, documentation, promptCache, queue);
        }

        return documentation;
    }

    private void seedSupportingTypes(Map<String, List<String>> documentation, ClassMetadata promptMetadata) {
        for (RelatedTypeMetadata type : promptMetadata.getSupportingTypes()) {
            String key = type.getQualifiedName();
            List<String> lines = documentation.computeIfAbsent(key, ignored -> new ArrayList<>());
            LinkedHashSet<String> entries = new LinkedHashSet<>(lines);
            for (MethodMetadata method : type.getMethods()) {
                entries.add(formatSignature(type.getClassName(), method));
            }
            if (type.isEnumType()) {
                entries.add(formatEnumConstants(type.getEnumConstants()));
            }
            if (type.getKind() != null && type.getKind().isRecord()) {
                entries.add("record type");
            }
            if (entries.isEmpty()) {
                entries.add("(no documented members)");
            }
            lines.clear();
            lines.addAll(entries);
        }
    }

    private void processGraph(com.example.tests.generator.model.ClassMetadata context,
                              MethodDependencyGraph graph,
                              Map<String, List<String>> documentation,
                              Map<String, ClassMetadata> promptCache,
                              Deque<PendingMethod> queue) {
        for (DependencyNode dependency : graph.getDependencies()) {
            processNode(context, dependency, documentation, promptCache, queue);
        }
        for (MethodInvocation invocation : graph.getUnattachedInvocations()) {
            for (InvocationArgument argument : invocation.getArguments()) {
                registerArgument(argument, context, documentation, promptCache);
            }
        }
    }

    private void processNode(com.example.tests.generator.model.ClassMetadata context,
                             DependencyNode node,
                             Map<String, List<String>> documentation,
                             Map<String, ClassMetadata> promptCache,
                             Deque<PendingMethod> queue) {
        Optional<com.example.tests.generator.model.ClassMetadata> dependencyMetadata =
                registerType(node.getType(), context, documentation, promptCache);

        for (InvocationArgument argument : node.getConstructorArguments()) {
            registerArgument(argument, context, documentation, promptCache);
        }

        for (MethodInvocation invocation : node.getMethodInvocations()) {
            for (InvocationArgument argument : invocation.getArguments()) {
                registerArgument(argument, context, documentation, promptCache);
            }
            dependencyMetadata.ifPresent(metadata -> queue.addLast(new PendingMethod(metadata, invocation.getName())));
        }

        for (DependencyNode child : node.getDependencies()) {
            processNode(context, child, documentation, promptCache, queue);
        }
    }

    private Optional<com.example.tests.generator.model.ClassMetadata> registerType(String rawType,
                                                       com.example.tests.generator.model.ClassMetadata context,
                                                       Map<String, List<String>> documentation,
                                                       Map<String, ClassMetadata> promptCache) {
        String sanitized = sanitizeType(rawType);
        if (sanitized.isEmpty()) {
            return Optional.empty();
        }

        Optional<com.example.tests.generator.model.ClassMetadata> resolved = resolveType(sanitized, context);
        if (resolved.isEmpty()) {
            return Optional.empty();
        }

        com.example.tests.generator.model.ClassMetadata metadata = resolved.get();
        ClassMetadata prompt = promptCache.computeIfAbsent(metadata.getQualifiedName(), fqcn -> metadataTransformer.transform(metadata));
        mergeDocumentation(documentation, prompt);
        return Optional.of(metadata);
    }

    private void registerArgument(InvocationArgument argument,
                                  com.example.tests.generator.model.ClassMetadata context,
                                  Map<String, List<String>> documentation,
                                  Map<String, ClassMetadata> promptCache) {
        if (registerType(argument.getType(), context, documentation, promptCache).isPresent()) {
            return;
        }
        String inferred = inferTypeFromExpression(argument.getExpression());
        if (!inferred.isEmpty()) {
            registerType(inferred, context, documentation, promptCache);
        }
    }

    private Optional<com.example.tests.generator.model.ClassMetadata> resolveType(String candidate,
                                                                                  com.example.tests.generator.model.ClassMetadata context) {
        if (candidate.isBlank()) {
            return Optional.empty();
        }

        Optional<com.example.tests.generator.model.ClassMetadata> direct =
                metadataTransformer.resolveRawMetadata(candidate, context.getPackageName());
        if (direct.isPresent()) {
            return direct;
        }

        for (String importName : context.getImports()) {
            if (importName.endsWith(".*")) {
                String fqcn = importName.substring(0, importName.length() - 2) + '.' + candidate;
                Optional<com.example.tests.generator.model.ClassMetadata> wildcard = metadataTransformer.findRawMetadata(fqcn);
                if (wildcard.isPresent()) {
                    return wildcard;
                }
                continue;
            }
            if (importName.endsWith('.' + candidate)) {
                Optional<com.example.tests.generator.model.ClassMetadata> resolved = metadataTransformer.findRawMetadata(importName);
                if (resolved.isPresent()) {
                    return resolved;
                }
            }
        }

        if (!context.getPackageName().isBlank()) {
            String fqcn = context.getPackageName() + '.' + candidate;
            Optional<com.example.tests.generator.model.ClassMetadata> samePackage = metadataTransformer.findRawMetadata(fqcn);
            if (samePackage.isPresent()) {
                return samePackage;
            }
        }

        return metadataTransformer.resolveRawMetadata(candidate, context.getPackageName());
    }

    private void mergeDocumentation(Map<String, List<String>> documentation, ClassMetadata metadata) {
        String key = metadata.getFullyQualifiedName();
        List<String> existing = documentation.computeIfAbsent(key, ignored -> new ArrayList<>());
        LinkedHashSet<String> entries = new LinkedHashSet<>(existing);
        for (MethodMetadata method : metadata.getMethods()) {
            entries.add(formatSignature(metadata.getClassName(), method));
        }
        if (metadata.isEnumType()) {
            entries.add(formatEnumConstants(metadata.getEnumConstants()));
        }
        ClassKind kind = metadata.getKind();
        if (kind != null && kind.isRecord()) {
            entries.add("record type");
        }
        if (entries.isEmpty()) {
            entries.add("(no documented members)");
        }
        existing.clear();
        existing.addAll(entries);
    }

    private List<String> selectMethodNames(com.example.tests.generator.model.ClassMetadata owner,
                                           String simpleTestClass,
                                           String testSource) {
        List<String> names = new ArrayList<>();
        if (testSource == null || testSource.isBlank()) {
            owner.getMethods().forEach(method -> names.add(method.getName()));
            return names;
        }
        for (com.example.tests.generator.model.MethodMetadata method : owner.getMethods()) {
            if (shouldAnalyse(method, owner.getClassName(), simpleTestClass, testSource)) {
                names.add(method.getName());
            }
        }
        return names;
    }

    private boolean shouldAnalyse(com.example.tests.generator.model.MethodMetadata method,
                                  String ownerSimpleName,
                                  String simpleTestClass,
                                  String testSource) {
        if (method.isConstructor()) {
            return testSource.contains("new " + ownerSimpleName + '(');
        }
        String invocationPattern = method.getName() + '(';
        if (testSource.contains(invocationPattern)) {
            return true;
        }
        if (simpleTestClass != null) {
            String qualifiedPattern = simpleTestClass + '.' + method.getName() + '(';
            return testSource.contains(qualifiedPattern);
        }
        return false;
    }

    private String formatSignature(String ownerSimpleName, MethodMetadata method) {
        String parameters = method.getParameters().stream()
                .map(parameter -> parameter.toString())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        if (method.isConstructor()) {
            return ownerSimpleName + '(' + parameters + ')';
        }
        String qualifier = method.isStaticMethod() ? "static " : "";
        return String.format(Locale.ENGLISH, "%s%s %s.%s(%s)", qualifier,
                method.getReturnType(), ownerSimpleName, method.getName(), parameters);
    }

    private String formatEnumConstants(List<String> constants) {
        if (constants == null || constants.isEmpty()) {
            return "enum constants: (undocumented)";
        }
        return "enum constants: " + String.join(", ", constants);
    }

    private String sanitizeType(String rawType) {
        if (rawType == null) {
            return "";
        }
        String trimmed = rawType.trim();
        if (trimmed.isEmpty() || trimmed.equals("Unknown") || trimmed.equals("null")) {
            return "";
        }
        trimmed = trimmed.replace("...", "").replace("[]", "");
        int genericStart = trimmed.indexOf('<');
        if (genericStart >= 0) {
            trimmed = trimmed.substring(0, genericStart);
        }
        if (trimmed.endsWith("()")) {
            trimmed = trimmed.substring(0, trimmed.length() - 2);
        }
        if (trimmed.contains(" ")) {
            trimmed = trimmed.substring(trimmed.lastIndexOf(' ') + 1);
        }
        trimmed = trimmed.replace('$', '.');
        if (trimmed.isEmpty()) {
            return "";
        }
        if (!trimmed.contains(".")) {
            char first = trimmed.charAt(0);
            if (!Character.isUpperCase(first)) {
                return "";
            }
        }
        return trimmed;
    }

    private String inferTypeFromExpression(String expression) {
        if (expression == null) {
            return "";
        }
        String trimmed = expression.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.startsWith("new ")) {
            String afterNew = trimmed.substring(4);
            int paren = afterNew.indexOf('(');
            String candidate = paren >= 0 ? afterNew.substring(0, paren) : afterNew;
            return sanitizeType(candidate);
        }
        int dot = trimmed.indexOf('.');
        if (dot > 0) {
            return sanitizeType(trimmed.substring(0, dot));
        }
        return sanitizeType(trimmed);
    }

    private record PendingMethod(com.example.tests.generator.model.ClassMetadata owner, String methodName) {
        String key() {
            return owner.getQualifiedName() + '#' + methodName;
        }
    }
}

