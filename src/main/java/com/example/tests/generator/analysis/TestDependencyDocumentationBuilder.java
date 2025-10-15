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

    public DependencyDocumentation buildDocumentation(ClassMetadata promptMetadata,
                                                      com.example.tests.generator.model.ClassMetadata ownerMetadata,
                                                      String testSource) {
        Map<String, List<String>> documentation = new LinkedHashMap<>();
        Map<String, ClassMetadata> promptCache = new LinkedHashMap<>();
        Set<String> processedTypes = new LinkedHashSet<>();
        Set<String> supportingTypes = new LinkedHashSet<>();

        if (promptMetadata != null) {
            promptCache.put(promptMetadata.getFullyQualifiedName(), promptMetadata);
            com.example.tests.generator.model.ClassMetadata promptRaw = metadataTransformer
                    .findRawMetadata(promptMetadata.getFullyQualifiedName())
                    .orElse(null);
            seedSupportingTypes(documentation, promptMetadata, promptRaw, promptCache, processedTypes,
                    supportingTypes);
        }

        if (ownerMetadata == null) {
            return new DependencyDocumentation(documentation, extractSupportingDocumentation(documentation,
                    supportingTypes));
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
            processGraph(pending.owner(), graph, documentation, promptCache, queue, processedTypes,
                    supportingTypes);
        }

        return new DependencyDocumentation(documentation, extractSupportingDocumentation(documentation,
                supportingTypes));
    }

    private void seedSupportingTypes(Map<String, List<String>> documentation,
                                     ClassMetadata promptMetadata,
                                     com.example.tests.generator.model.ClassMetadata promptRaw,
                                     Map<String, ClassMetadata> promptCache,
                                     Set<String> processedTypes,
                                     Set<String> supportingTypes) {
        for (RelatedTypeMetadata type : promptMetadata.getSupportingTypes()) {
            registerType(type.getQualifiedName(), promptRaw, documentation, promptCache, processedTypes,
                    supportingTypes, false);
        }
    }

    private void processGraph(com.example.tests.generator.model.ClassMetadata context,
                              MethodDependencyGraph graph,
                              Map<String, List<String>> documentation,
                              Map<String, ClassMetadata> promptCache,
                              Deque<PendingMethod> queue,
                              Set<String> processedTypes,
                              Set<String> supportingTypes) {
        for (DependencyNode dependency : graph.getDependencies()) {
            processNode(context, dependency, documentation, promptCache, queue, processedTypes, supportingTypes);
        }
        for (MethodInvocation invocation : graph.getUnattachedInvocations()) {
            for (InvocationArgument argument : invocation.getArguments()) {
                registerArgument(argument, context, documentation, promptCache, processedTypes, supportingTypes);
            }
        }
    }

    private void processNode(com.example.tests.generator.model.ClassMetadata context,
                             DependencyNode node,
                             Map<String, List<String>> documentation,
                             Map<String, ClassMetadata> promptCache,
                              Deque<PendingMethod> queue,
                             Set<String> processedTypes,
                             Set<String> supportingTypes) {
        Optional<com.example.tests.generator.model.ClassMetadata> dependencyMetadata =
                registerType(node.getType(), context, documentation, promptCache, processedTypes, supportingTypes,
                        false);

        for (InvocationArgument argument : node.getConstructorArguments()) {
            registerArgument(argument, context, documentation, promptCache, processedTypes, supportingTypes);
        }

        for (MethodInvocation invocation : node.getMethodInvocations()) {
            for (InvocationArgument argument : invocation.getArguments()) {
                registerArgument(argument, context, documentation, promptCache, processedTypes, supportingTypes);
            }
            dependencyMetadata.ifPresent(metadata -> registerReturnType(metadata, invocation,
                    context, documentation, promptCache, processedTypes, supportingTypes));
            dependencyMetadata.ifPresent(metadata -> queue.addLast(new PendingMethod(metadata, invocation.getName())));
        }

        for (DependencyNode child : node.getDependencies()) {
            processNode(context, child, documentation, promptCache, queue, processedTypes, supportingTypes);
        }
    }

    private void registerReturnType(com.example.tests.generator.model.ClassMetadata owner,
                                    MethodInvocation invocation,
                                    com.example.tests.generator.model.ClassMetadata context,
                                    Map<String, List<String>> documentation,
                                    Map<String, ClassMetadata> promptCache,
                                    Set<String> processedTypes,
                                    Set<String> supportingTypes) {
        if (invocation.getName() == null || invocation.getName().isBlank()) {
            return;
        }
        for (com.example.tests.generator.model.MethodMetadata method : owner.getMethods()) {
            if (!invocation.getName().equals(method.getName())) {
                continue;
            }
            if (method.isConstructor()) {
                continue;
            }
            registerType(method.getReturnType(), context, documentation, promptCache, processedTypes, supportingTypes,
                    true);
        }
    }

    private Optional<com.example.tests.generator.model.ClassMetadata> registerType(String rawType,
                                                       com.example.tests.generator.model.ClassMetadata context,
                                                       Map<String, List<String>> documentation,
                                                       Map<String, ClassMetadata> promptCache,
                                                       Set<String> processedTypes,
                                                       Set<String> supportingTypes,
                                                       boolean markSupporting) {
        List<String> candidates = extractTypeCandidates(rawType);
        Optional<com.example.tests.generator.model.ClassMetadata> primary = Optional.empty();
        for (String candidate : candidates) {
            Optional<com.example.tests.generator.model.ClassMetadata> resolved = resolveType(candidate, context);
            if (resolved.isEmpty()) {
                continue;
            }

            com.example.tests.generator.model.ClassMetadata metadata = resolved.get();
            if (isJavaCorePackage(metadata.getPackageName())) {
                continue;
            }
            if (markSupporting) {
                supportingTypes.add(metadata.getQualifiedName());
            }
            ClassMetadata prompt = promptCache.computeIfAbsent(metadata.getQualifiedName(),
                    fqcn -> metadataTransformer.transform(metadata));
            boolean newlyProcessed = processedTypes.add(metadata.getQualifiedName());
            if (newlyProcessed) {
                mergeDocumentation(documentation, metadata, prompt, promptCache, processedTypes, supportingTypes);
            }
            if (primary.isEmpty()) {
                primary = Optional.of(metadata);
            }
        }
        return primary;
    }

    private void registerArgument(InvocationArgument argument,
                                  com.example.tests.generator.model.ClassMetadata context,
                                  Map<String, List<String>> documentation,
                                  Map<String, ClassMetadata> promptCache,
                                  Set<String> processedTypes,
                                  Set<String> supportingTypes) {
        if (registerType(argument.getType(), context, documentation, promptCache, processedTypes, supportingTypes,
                true).isPresent()) {
            return;
        }
        String inferred = inferTypeFromExpression(argument.getExpression());
        if (!inferred.isEmpty()) {
            registerType(inferred, context, documentation, promptCache, processedTypes, supportingTypes, true);
        }
    }

    private Optional<com.example.tests.generator.model.ClassMetadata> resolveType(String candidate,
                                                                                  com.example.tests.generator.model.ClassMetadata context) {
        if (candidate.isBlank()) {
            return Optional.empty();
        }

        Optional<com.example.tests.generator.model.ClassMetadata> fqcn =
                metadataTransformer.findRawMetadata(candidate);
        if (fqcn.isPresent()) {
            return fqcn;
        }

        if (context == null) {
            return metadataTransformer.resolveRawMetadata(candidate, "");
        }

        Optional<com.example.tests.generator.model.ClassMetadata> direct =
                metadataTransformer.resolveRawMetadata(candidate, context.getPackageName());
        if (direct.isPresent()) {
            return direct;
        }

        for (String importName : context.getImports()) {
            if (importName.endsWith(".*")) {
                String candidateFqcn = importName.substring(0, importName.length() - 2) + '.' + candidate;
                Optional<com.example.tests.generator.model.ClassMetadata> wildcard =
                        metadataTransformer.findRawMetadata(candidateFqcn);
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
            String candidateFqcn = context.getPackageName() + '.' + candidate;
            Optional<com.example.tests.generator.model.ClassMetadata> samePackage =
                    metadataTransformer.findRawMetadata(candidateFqcn);
            if (samePackage.isPresent()) {
                return samePackage;
            }
        }

        return metadataTransformer.resolveRawMetadata(candidate, context.getPackageName());
    }

    private void mergeDocumentation(Map<String, List<String>> documentation,
                                    com.example.tests.generator.model.ClassMetadata rawMetadata,
                                    ClassMetadata metadata,
                                    Map<String, ClassMetadata> promptCache,
                                    Set<String> processedTypes,
                                    Set<String> supportingTypes) {
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

        for (com.example.tests.generator.model.MethodMetadata method : rawMetadata.getMethods()) {
            registerMethodSignatureTypes(method, rawMetadata, documentation, promptCache, processedTypes,
                    supportingTypes);
        }
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
                .map(parameter -> simplifyType(parameter.getType()) + ' ' + parameter.getName())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        if (method.isConstructor()) {
            return ownerSimpleName + '(' + parameters + ')';
        }
        String qualifier = method.isStaticMethod() ? "static " : "";
        String returnType = simplifyType(method.getReturnType());
        return String.format(Locale.ENGLISH, "%s%s %s.%s(%s)", qualifier,
                returnType, ownerSimpleName, method.getName(), parameters);
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
        trimmed = trimmed.replace("...", "");
        int arrayIndex = trimmed.indexOf('[');
        if (arrayIndex >= 0) {
            trimmed = trimmed.substring(0, arrayIndex);
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

    private String simplifyType(String type) {
        if (type == null || type.isBlank()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < type.length(); i++) {
            char ch = type.charAt(i);
            if (Character.isJavaIdentifierPart(ch) || ch == '.' || ch == '$') {
                token.append(ch);
            } else {
                appendSimplifiedToken(result, token);
                result.append(ch);
            }
        }
        appendSimplifiedToken(result, token);
        return result.toString();
    }

    private void appendSimplifiedToken(StringBuilder result, StringBuilder token) {
        if (token.length() == 0) {
            return;
        }
        String candidate = token.toString();
        int lastDot = candidate.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < candidate.length() - 1) {
            candidate = candidate.substring(lastDot + 1);
        }
        int lastDollar = candidate.lastIndexOf('$');
        if (lastDollar >= 0 && lastDollar < candidate.length() - 1) {
            candidate = candidate.substring(lastDollar + 1);
        }
        result.append(candidate);
        token.setLength(0);
    }

    private List<String> extractTypeCandidates(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return List.of();
        }
        String normalized = rawType.replace("...", "");
        List<String> candidates = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (Character.isJavaIdentifierPart(ch) || ch == '.' || ch == '$') {
                token.append(ch);
            } else {
                collectCandidateToken(candidates, token);
            }
        }
        collectCandidateToken(candidates, token);
        return candidates;
    }

    private void collectCandidateToken(List<String> candidates, StringBuilder token) {
        if (token.length() == 0) {
            return;
        }
        String candidate = token.toString();
        token.setLength(0);
        candidate = candidate.replace('$', '.');
        if (candidate.isEmpty()) {
            return;
        }
        int lastDot = candidate.lastIndexOf('.');
        int start = Math.max(lastDot, -1) + 1;
        if (start >= candidate.length()) {
            return;
        }
        char first = candidate.charAt(start);
        if (!Character.isUpperCase(first)) {
            return;
        }
        if (!candidates.contains(candidate)) {
            candidates.add(candidate);
        }
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

    private void registerMethodSignatureTypes(com.example.tests.generator.model.MethodMetadata method,
                                              com.example.tests.generator.model.ClassMetadata owner,
                                              Map<String, List<String>> documentation,
                                              Map<String, ClassMetadata> promptCache,
                                              Set<String> processedTypes,
                                              Set<String> supportingTypes) {
        if (!method.isConstructor()) {
            registerType(method.getReturnType(), owner, documentation, promptCache, processedTypes, supportingTypes,
                    true);
        }
        for (com.example.tests.generator.model.MethodMetadata.Parameter parameter : method.getParameters()) {
            registerType(parameter.getType(), owner, documentation, promptCache, processedTypes, supportingTypes,
                    true);
        }
    }

    private Map<String, List<String>> extractSupportingDocumentation(Map<String, List<String>> documentation,
                                                                     Set<String> supportingTypes) {
        Map<String, List<String>> supporting = new LinkedHashMap<>();
        for (String qualifiedName : supportingTypes) {
            List<String> members = documentation.get(qualifiedName);
            if (members == null) {
                continue;
            }
            supporting.put(qualifiedName, new ArrayList<>(members));
        }
        return supporting;
    }

    private boolean isJavaCorePackage(String packageName) {
        if (packageName == null || packageName.isBlank()) {
            return false;
        }
        return packageName.startsWith("java.")
                || packageName.startsWith("javax.")
                || packageName.startsWith("jakarta.");
    }

    private record PendingMethod(com.example.tests.generator.model.ClassMetadata owner, String methodName) {
        String key() {
            return owner.getQualifiedName() + '#' + methodName;
        }
    }
}

