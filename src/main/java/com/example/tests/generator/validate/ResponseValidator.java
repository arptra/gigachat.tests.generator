package com.example.tests.generator.validate;

import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import com.example.tests.generator.metadata.ClassMetadata;
import com.example.tests.generator.metadata.RelatedTypeMetadata;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;

import java.net.URI;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates that the response returned by the agent contains a compilable Java test class.
 */
public class ResponseValidator {

    private static final List<String> REQUIRED_IMPORT_PREFIXES = List.of("org.junit.jupiter", "org.mockito");
    private static final List<String> DISALLOWED_IMPORT_PREFIXES = List.of("org.assertj");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([a-zA-Z0-9_.]+)\\s*;");
    private static final Pattern TYPE_PATTERN = Pattern.compile("(?m)^\\s*public\\s+(?:[A-Za-z]+\\s+)*(class|interface|enum|record)\\s+([A-Za-z0-9_]+)");

    private final JavaCompiler compiler;

    public ResponseValidator() {
        this.compiler = ToolProvider.getSystemJavaCompiler();
        if (this.compiler == null) {
            throw new IllegalStateException("Java compiler is not available. Ensure a JDK is installed.");
        }
    }

    public ValidationResult validate(String response) {
        return validate(response, null);
    }

    public ValidationResult validate(String response, ClassMetadata metadata) {
        List<String> errors = new ArrayList<>();
        if (response == null || response.isBlank()) {
            errors.add("Empty response provided.");
            return ValidationResult.failure(errors);
        }

        Optional<String> code = ResponseUtils.extractJavaCodeBlock(response);
        if (code.isEmpty()) {
            code = ResponseUtils.extractJavaSnippet(response);
            if (code.isEmpty()) {
                errors.add("Response does not contain a Java code block fenced with ```java``` or recognizable Java source.");
                return ValidationResult.failure(errors);
            }
        }

        ParseResult parseResult = parse(code.get());
        errors.addAll(parseResult.errors);
        if (!parseResult.errors.isEmpty()) {
            return ValidationResult.failure(errors);
        }

        if (!hasTestClass(parseResult.compilationUnits)) {
            errors.add("No public test class found (class name should end with 'Test').");
        }
        if (!hasTestAnnotation(parseResult.compilationUnits)) {
            errors.add("No methods annotated with @Test were found.");
        }
        if (!containsRequiredImports(parseResult.compilationUnits)) {
            errors.add("Missing required imports for JUnit Jupiter or Mockito.");
        }

        if (containsDisallowedImports(parseResult.compilationUnits)) {
            errors.add("Disallowed assertion libraries detected (e.g., AssertJ). Use only JUnit and Mockito.");
        }

        if (metadata != null) {
            errors.addAll(checkInstantiationRestrictions(parseResult.compilationUnits, metadata));
            errors.addAll(checkSupportedApiUsage(parseResult.compilationUnits, metadata));
            errors.addAll(simulateCompilation(code.get(), metadata));
        }

        return errors.isEmpty() ? ValidationResult.success(code.get()) : ValidationResult.failure(errors);
    }

    private ParseResult parse(String code) {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        InMemoryJavaFile file = new InMemoryJavaFile("GeneratedTest", code);
        JavacTask task = (JavacTask) compiler.getTask(null, null, diagnostics, List.of("-proc:none"), null, List.of(file));

        List<String> errors = new ArrayList<>();
        List<CompilationUnitTree> units = new ArrayList<>();
        try {
            for (CompilationUnitTree unit : task.parse()) {
                units.add(unit);
            }
        } catch (Exception e) {
            errors.add("Failed to parse Java code: " + e.getMessage());
        }

        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                errors.add(String.format(Locale.ENGLISH, "Compilation error at line %d: %s",
                        diagnostic.getLineNumber(), diagnostic.getMessage(Locale.ENGLISH)));
            }
        }

        return new ParseResult(units, errors);
    }

    private boolean hasTestClass(List<CompilationUnitTree> units) {
        return units.stream()
                .flatMap(unit -> unit.getTypeDecls().stream())
                .filter(tree -> tree instanceof ClassTree)
                .map(tree -> (ClassTree) tree)
                .anyMatch(classTree -> classTree.getSimpleName().toString().endsWith("Test") && classTree.getModifiers().getFlags().contains(Modifier.PUBLIC));
    }

    private boolean hasTestAnnotation(List<CompilationUnitTree> units) {
        return units.stream()
                .flatMap(unit -> unit.getTypeDecls().stream())
                .filter(tree -> tree instanceof ClassTree)
                .map(tree -> (ClassTree) tree)
                .flatMap(classTree -> classTree.getMembers().stream())
                .filter(member -> member instanceof MethodTree)
                .map(member -> (MethodTree) member)
                .anyMatch(method -> method.getModifiers().getAnnotations().stream()
                        .map(this::annotationSimpleName)
                        .anyMatch(name -> name.equals("Test")));
    }

    private boolean containsRequiredImports(List<CompilationUnitTree> units) {
        return units.stream()
                .flatMap(unit -> unit.getImports().stream())
                .map(this::importQualifiedName)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .anyMatch(importName -> REQUIRED_IMPORT_PREFIXES.stream()
                        .anyMatch(importName::startsWith));
    }

    private boolean containsDisallowedImports(List<CompilationUnitTree> units) {
        return units.stream()
                .flatMap(unit -> unit.getImports().stream())
                .map(this::importQualifiedName)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .anyMatch(importName -> DISALLOWED_IMPORT_PREFIXES.stream()
                        .anyMatch(importName::startsWith));
    }

    private List<String> checkInstantiationRestrictions(List<CompilationUnitTree> units, ClassMetadata metadata) {
        Set<String> bannedSimpleNames = new HashSet<>();
        Set<String> bannedQualifiedNames = new HashSet<>();
        String qualifiedName = metadata.getPackageName() == null || metadata.getPackageName().isBlank()
                ? metadata.getClassName()
                : metadata.getPackageName() + '.' + metadata.getClassName();
        registerRestrictedType(metadata.getClassName(), qualifiedName, metadata.isInterface(),
                metadata.isAbstractType(), metadata.isEnumType(), bannedSimpleNames, bannedQualifiedNames);
        for (RelatedTypeMetadata type : metadata.getSupportingTypes()) {
            boolean isInterface = type.getKind() != null && type.getKind().isInterface();
            registerRestrictedType(type.getClassName(), type.getQualifiedName(), isInterface,
                    type.isAbstractType(), type.isEnumType(), bannedSimpleNames, bannedQualifiedNames);
        }
        if (bannedSimpleNames.isEmpty()) {
            return List.of();
        }
        Set<String> violations = new HashSet<>();
        TreeScanner<Void, Void> scanner = new TreeScanner<>() {
            @Override
            public Void visitNewClass(NewClassTree node, Void unused) {
                String identifier = node.getIdentifier().toString();
                if (isInstantiationRestricted(identifier, bannedSimpleNames, bannedQualifiedNames)) {
                    String simple = extractSimpleName(identifier);
                    violations.add(String.format(Locale.ENGLISH,
                            "Do not instantiate %s directly; use the documented API such as mocks or enum constants.", simple));
                }
                return super.visitNewClass(node, unused);
            }
        };
        for (CompilationUnitTree unit : units) {
            scanner.scan(unit, null);
        }
        return new ArrayList<>(violations);
    }

    private List<String> checkSupportedApiUsage(List<CompilationUnitTree> units, ClassMetadata metadata) {
        ApiUsageScanner scanner = new ApiUsageScanner(metadata);
        for (CompilationUnitTree unit : units) {
            scanner.scan(unit, null);
        }
        return scanner.errors();
    }

    private void registerRestrictedType(String simpleName,
                                        String qualifiedName,
                                        boolean interfaceType,
                                        boolean abstractType,
                                        boolean enumType,
                                        Set<String> simpleNames,
                                        Set<String> qualifiedNames) {
        if (!(interfaceType || abstractType || enumType)) {
            return;
        }
        if (simpleName == null || simpleName.isBlank()) {
            return;
        }
        simpleNames.add(simpleName);
        if (qualifiedName != null && !qualifiedName.isBlank()) {
            qualifiedNames.add(qualifiedName);
        }
    }

    private boolean isInstantiationRestricted(String identifier,
                                               Set<String> bannedSimple,
                                               Set<String> bannedQualified) {
        if (identifier == null || identifier.isBlank()) {
            return false;
        }
        if (bannedQualified.contains(identifier)) {
            return true;
        }
        String simple = extractSimpleName(identifier);
        return bannedSimple.contains(simple);
    }

    private static String extractSimpleName(String identifier) {
        int lastDot = identifier.lastIndexOf('.');
        return lastDot >= 0 ? identifier.substring(lastDot + 1) : identifier;
    }

    private String annotationSimpleName(AnnotationTree annotation) {
        return annotation.getAnnotationType().toString().replaceAll("^.*\\.", "");
    }

    private Optional<String> importQualifiedName(ImportTree importTree) {
        return Optional.ofNullable(importTree.getQualifiedIdentifier()).map(Object::toString);
    }

    private List<String> simulateCompilation(String testSource, ClassMetadata metadata) {
        StubSourceRegistry registry = new StubSourceRegistry();
        registry.register(metadata);
        metadata.getSupportingTypes().forEach(registry::register);

        List<JavaFileObject> sources = new ArrayList<>();
        String primaryType = inferPrimaryTypeName(testSource);
        String primarySimpleName = extractSimpleName(primaryType);
        sources.add(new InMemoryJavaFile(primaryType, testSource));
        registry.createSources().forEach((name, source) -> sources.add(new InMemoryJavaFile(name, source)));
        SupportLibraryStubs.getSources().forEach((name, source) -> sources.add(new InMemoryJavaFile(name, source)));

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler.CompilationTask task = compiler.getTask(null, null, diagnostics, List.of("-proc:none"), null, sources);
        Boolean result = task.call();
        if (Boolean.TRUE.equals(result)) {
            return List.of();
        }

        List<String> errors = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() != Diagnostic.Kind.ERROR) {
                continue;
            }
            JavaFileObject source = diagnostic.getSource();
            if (source == null || !primarySimpleName.equals(simpleSourceName(source))) {
                continue;
            }
            errors.add(String.format(Locale.ENGLISH,
                    "Compilation error at line %d: %s",
                    diagnostic.getLineNumber(),
                    diagnostic.getMessage(Locale.ENGLISH)));
        }
        return errors;
    }

    private String simpleSourceName(JavaFileObject source) {
        String name = source.getName();
        if (name == null) {
            return "";
        }
        int lastSlash = name.lastIndexOf('/') + 1;
        int lastDot = name.lastIndexOf('.');
        if (lastDot <= lastSlash) {
            return name.substring(lastSlash);
        }
        return name.substring(lastSlash, lastDot);
    }

    private String inferPrimaryTypeName(String source) {
        if (source == null || source.isBlank()) {
            return "GeneratedTest";
        }
        String packageName = "";
        Matcher packageMatcher = PACKAGE_PATTERN.matcher(source);
        if (packageMatcher.find()) {
            packageName = packageMatcher.group(1);
        }
        Matcher typeMatcher = TYPE_PATTERN.matcher(source);
        String simpleName = null;
        if (typeMatcher.find()) {
            simpleName = typeMatcher.group(2);
        }
        if (simpleName == null || simpleName.isBlank()) {
            simpleName = "GeneratedTest";
        }
        if (packageName.isBlank()) {
            return simpleName;
        }
        return packageName + '.' + simpleName;
    }

    private static final class ApiUsageScanner extends TreeScanner<Void, Void> {

        private static final Set<String> COMMON_INSTANCE_METHODS = Set.of("equals", "hashCode", "toString");
        private static final Set<String> ENUM_METHODS = Set.of("values", "valueOf", "name", "ordinal", "compareTo");

        private final Map<String, DomainTypeUsage> domainTypes;
        private final Deque<Map<String, String>> scopes = new ArrayDeque<>();
        private final Set<Tree> methodInvocationSelectors = Collections.newSetFromMap(new IdentityHashMap<>());
        private final LinkedHashSet<String> violations = new LinkedHashSet<>();

        private ApiUsageScanner(ClassMetadata metadata) {
            this.domainTypes = buildDomainTypeUsage(metadata);
            this.scopes.push(new LinkedHashMap<>());
        }

        private List<String> errors() {
            return new ArrayList<>(violations);
        }

        @Override
        public Void visitClass(ClassTree node, Void unused) {
            pushScope();
            super.visitClass(node, unused);
            popScope();
            return null;
        }

        @Override
        public Void visitMethod(MethodTree node, Void unused) {
            pushScope();
            for (VariableTree parameter : node.getParameters()) {
                declare(parameter.getName().toString(), extractSimpleName(parameter.getType().toString()));
            }
            super.visitMethod(node, unused);
            popScope();
            return null;
        }

        @Override
        public Void visitVariable(VariableTree node, Void unused) {
            if (node.getType() != null) {
                declare(node.getName().toString(), extractSimpleName(node.getType().toString()));
            }
            return super.visitVariable(node, unused);
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
            ExpressionTree select = node.getMethodSelect();
            if (select != null) {
                methodInvocationSelectors.add(select);
            }
            if (select instanceof MemberSelectTree memberSelect) {
                String methodName = memberSelect.getIdentifier().toString();
                String ownerType = resolveOwnerType(memberSelect.getExpression());
                if (ownerType != null) {
                    DomainTypeUsage usage = domainTypes.get(ownerType);
                    if (usage != null && !usage.isMethodAllowed(methodName)) {
                        violations.add(String.format(Locale.ENGLISH,
                                "Method %s.%s(..) is not part of the documented API. Allowed methods: %s",
                                usage.simpleName,
                                methodName,
                                usage.describeAllowedMethods()));
                    }
                }
            }
            return super.visitMethodInvocation(node, unused);
        }

        @Override
        public Void visitNewClass(NewClassTree node, Void unused) {
            String identifier = node.getIdentifier() == null ? null : node.getIdentifier().toString();
            String simpleName = extractSimpleName(identifier);
            DomainTypeUsage usage = domainTypes.get(simpleName);
            if (usage != null && !usage.matchesConstructorArity(node.getArguments().size())) {
                violations.add(String.format(Locale.ENGLISH,
                        "%s does not declare a constructor accepting %d argument(s). Use one of the documented constructors.",
                        usage.simpleName,
                        node.getArguments().size()));
            }
            return super.visitNewClass(node, unused);
        }

        @Override
        public Void visitMemberSelect(MemberSelectTree node, Void unused) {
            if (methodInvocationSelectors.contains(node)) {
                return super.visitMemberSelect(node, unused);
            }
            String expressionText = node.getExpression().toString();
            String memberName = node.getIdentifier().toString();
            String ownerType = resolveOwnerType(node.getExpression());
            if (ownerType != null) {
                DomainTypeUsage usage = domainTypes.get(ownerType);
                if (usage != null) {
                    if (isVariable(expressionText) && usage.hasMethod(memberName)) {
                        violations.add(String.format(Locale.ENGLISH,
                                "Direct field access %s.%s is not part of the documented API. Invoke the accessor method instead.",
                                usage.simpleName,
                                memberName));
                    }
                    if (!isVariable(expressionText) && usage.isEnum) {
                        if (!"class".equals(memberName) && !usage.enumConstants.contains(memberName)) {
                            violations.add(String.format(Locale.ENGLISH,
                                    "%s does not declare enum constant %s. Use one of: %s",
                                    usage.simpleName,
                                    memberName,
                                    usage.describeEnumConstants()));
                        }
                    }
                }
            }
            return super.visitMemberSelect(node, unused);
        }

        private Map<String, DomainTypeUsage> buildDomainTypeUsage(ClassMetadata metadata) {
            Map<String, DomainTypeUsage> usages = new LinkedHashMap<>();
            register(usages, metadata.getClassName(), metadata.isEnumType(), metadata.getEnumConstants(), metadata.getMethods());
            for (RelatedTypeMetadata related : metadata.getSupportingTypes()) {
                register(usages, related.getClassName(), related.isEnumType(), related.getEnumConstants(), related.getMethods());
            }
            return usages;
        }

        private void register(Map<String, DomainTypeUsage> usages,
                              String simpleName,
                              boolean enumType,
                              List<String> enumConstants,
                              List<com.example.tests.generator.metadata.MethodMetadata> methods) {
            if (simpleName == null || simpleName.isBlank()) {
                return;
            }
            DomainTypeUsage usage = usages.computeIfAbsent(simpleName, DomainTypeUsage::new);
            usage.setEnum(enumType);
            if (enumConstants != null) {
                enumConstants.forEach(usage::addEnumConstant);
            }
            for (com.example.tests.generator.metadata.MethodMetadata method : methods) {
                if (method.isConstructor()) {
                    usage.addConstructor(method.getParameters().size());
                } else {
                    usage.addMethod(method.getName());
                }
            }
        }

        private void pushScope() {
            scopes.push(new LinkedHashMap<>());
        }

        private void popScope() {
            if (!scopes.isEmpty()) {
                scopes.pop();
            }
        }

        private void declare(String name, String type) {
            if (name == null || name.isBlank() || type == null || type.isBlank()) {
                return;
            }
            if (scopes.isEmpty()) {
                scopes.push(new LinkedHashMap<>());
            }
            scopes.peek().put(name, extractSimpleName(type));
        }

        private boolean isVariable(String expression) {
            if (expression == null || expression.isBlank()) {
                return false;
            }
            for (Map<String, String> scope : scopes) {
                if (scope.containsKey(expression)) {
                    return true;
                }
            }
            return false;
        }

        private String resolveOwnerType(ExpressionTree expression) {
            if (expression instanceof IdentifierTree identifier) {
                String name = identifier.getName().toString();
                String resolved = resolve(name);
                if (resolved != null) {
                    return resolved;
                }
                return domainTypes.containsKey(name) ? name : null;
            }
            String text = expression == null ? null : expression.toString();
            String simple = extractSimpleName(text);
            return domainTypes.containsKey(simple) ? simple : null;
        }

        private String resolve(String name) {
            if (name == null || name.isBlank()) {
                return null;
            }
            for (Map<String, String> scope : scopes) {
                String type = scope.get(name);
                if (type != null) {
                    return type;
                }
            }
            return null;
        }

        private static final class DomainTypeUsage {
            private final String simpleName;
            private final Set<String> methods = new LinkedHashSet<>();
            private final Set<Integer> constructorArities = new LinkedHashSet<>();
            private final Set<String> enumConstants = new LinkedHashSet<>();
            private boolean isEnum;

            private DomainTypeUsage(String simpleName) {
                this.simpleName = simpleName;
            }

            private void setEnum(boolean enumType) {
                this.isEnum = this.isEnum || enumType;
            }

            private void addMethod(String name) {
                if (name != null && !name.isBlank()) {
                    methods.add(name);
                }
            }

            private boolean hasMethod(String name) {
                return name != null && methods.contains(name);
            }

            private void addConstructor(int arity) {
                constructorArities.add(arity);
            }

            private void addEnumConstant(String constant) {
                if (constant != null && !constant.isBlank()) {
                    enumConstants.add(constant);
                }
            }

            private boolean isMethodAllowed(String name) {
                if (name == null || name.isBlank()) {
                    return true;
                }
                if (methods.contains(name)) {
                    return true;
                }
                if (COMMON_INSTANCE_METHODS.contains(name)) {
                    return true;
                }
                return isEnum && ENUM_METHODS.contains(name);
            }

            private boolean matchesConstructorArity(int arity) {
                if (constructorArities.isEmpty()) {
                    return true;
                }
                return constructorArities.contains(arity);
            }

            private String describeAllowedMethods() {
                if (methods.isEmpty()) {
                    return "(no instance methods available)";
                }
                return String.join(", ", methods);
            }

            private String describeEnumConstants() {
                if (enumConstants.isEmpty()) {
                    return "(no constants documented)";
                }
                return String.join(", ", enumConstants);
            }
        }
    }

    private static class ParseResult {
        private final List<CompilationUnitTree> compilationUnits;
        private final List<String> errors;

        private ParseResult(List<CompilationUnitTree> compilationUnits, List<String> errors) {
            this.compilationUnits = compilationUnits;
            this.errors = errors;
        }
    }

    private static class InMemoryJavaFile extends SimpleJavaFileObject {
        private final String code;

        private InMemoryJavaFile(String className, String code) {
            super(URI.create("string:///" + className.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension),
                    JavaFileObject.Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    private static final class StubSourceRegistry {

        private final Map<String, StubType> types = new LinkedHashMap<>();

        private void register(ClassMetadata metadata) {
            if (metadata == null) {
                return;
            }
            String qualifiedName = metadata.getPackageName() == null || metadata.getPackageName().isBlank()
                    ? metadata.getClassName()
                    : metadata.getPackageName() + '.' + metadata.getClassName();
            types.computeIfAbsent(qualifiedName, ignored -> new StubType(
                    metadata.getPackageName(),
                    metadata.getClassName(),
                    metadata.getKind(),
                    metadata.isAbstractType(),
                    metadata.getEnumConstants(),
                    metadata.getMethods()));
        }

        private void register(RelatedTypeMetadata metadata) {
            if (metadata == null) {
                return;
            }
            String qualifiedName = metadata.getQualifiedName();
            if (qualifiedName == null || qualifiedName.isBlank()) {
                qualifiedName = metadata.getPackageName() == null || metadata.getPackageName().isBlank()
                        ? metadata.getClassName()
                        : metadata.getPackageName() + '.' + metadata.getClassName();
            }
            String finalQualifiedName = qualifiedName;
            types.computeIfAbsent(finalQualifiedName, ignored -> new StubType(
                    metadata.getPackageName(),
                    metadata.getClassName(),
                    metadata.getKind(),
                    metadata.isAbstractType(),
                    metadata.getEnumConstants(),
                    metadata.getMethods()));
        }

        private Map<String, String> createSources() {
            Map<String, String> sources = new LinkedHashMap<>();
            for (Map.Entry<String, StubType> entry : types.entrySet()) {
                sources.put(entry.getKey(), entry.getValue().toSource());
            }
            return sources;
        }
    }

    private static final class StubType {

        private final String packageName;
        private final String simpleName;
        private final com.example.tests.generator.model.ClassKind kind;
        private final boolean abstractType;
        private final List<String> enumConstants;
        private final List<com.example.tests.generator.metadata.MethodMetadata> methods;

        private StubType(String packageName,
                         String simpleName,
                         com.example.tests.generator.model.ClassKind kind,
                         boolean abstractType,
                         List<String> enumConstants,
                         List<com.example.tests.generator.metadata.MethodMetadata> methods) {
            this.packageName = packageName == null ? "" : packageName;
            this.simpleName = simpleName;
            this.kind = kind == null ? com.example.tests.generator.model.ClassKind.CLASS : kind;
            this.abstractType = abstractType;
            this.enumConstants = enumConstants == null ? List.of() : enumConstants;
            this.methods = methods == null ? List.of() : methods;
        }

        private String toSource() {
            StringBuilder builder = new StringBuilder();
            if (!packageName.isBlank()) {
                builder.append("package ").append(packageName).append(";\n\n");
            }
            builder.append("import java.util.*;\n");
            builder.append("import java.time.*;\n");
            builder.append("import java.math.*;\n\n");

            switch (kind) {
                case INTERFACE -> builder.append("public interface ").append(simpleName).append(" {\n");
                case ENUM -> {
                    builder.append("public enum ").append(simpleName).append(" {\n");
                    if (enumConstants.isEmpty()) {
                        builder.append("    PLACEHOLDER;\n\n");
                    } else {
                        builder.append("    ").append(String.join(", ", enumConstants)).append(";\n\n");
                    }
                }
                default -> {
                    builder.append(abstractType ? "public abstract class " : "public class ")
                            .append(simpleName)
                            .append(" {\n");
                }
            }

            for (com.example.tests.generator.metadata.MethodMetadata method : methods) {
                builder.append(renderMethod(method));
            }

            builder.append("}\n");
            return builder.toString();
        }

        private String renderMethod(com.example.tests.generator.metadata.MethodMetadata method) {
            StringBuilder builder = new StringBuilder();
            if (method.isConstructor()) {
                if (kind == com.example.tests.generator.model.ClassKind.ENUM) {
                    builder.append("    private ");
                } else {
                    builder.append("    public ");
                }
                builder.append(simpleName);
                builder.append('(').append(renderParameters(method)).append(") {\n");
                builder.append("    }\n\n");
                return builder.toString();
            }

            builder.append("    ");
            if (kind == com.example.tests.generator.model.ClassKind.INTERFACE) {
                if (method.isStaticMethod()) {
                    builder.append("static ");
                }
            } else {
                if (method.isStaticMethod()) {
                    builder.append("public static ");
                } else {
                    builder.append("public ");
                }
            }
            if (kind == com.example.tests.generator.model.ClassKind.INTERFACE) {
                builder.append(method.getReturnType()).append(' ').append(method.getName())
                        .append('(').append(renderParameters(method)).append(')');
                if (method.isStaticMethod()) {
                    builder.append(" {\n");
                    if (!"void".equals(method.getReturnType())) {
                        builder.append("        return ").append(defaultValue(method.getReturnType())).append(";\n");
                    }
                    builder.append("    }\n\n");
                } else {
                    builder.append(";\n\n");
                }
                return builder.toString();
            }
            builder.append(method.getReturnType()).append(' ').append(method.getName())
                    .append('(').append(renderParameters(method)).append(") {");
            String returnType = method.getReturnType();
            if (!"void".equals(returnType)) {
                builder.append("\n        return ").append(defaultValue(returnType)).append(";\n    }");
            } else {
                builder.append("\n    }");
            }
            builder.append("\n\n");
            return builder.toString();
        }

        private String renderParameters(com.example.tests.generator.metadata.MethodMetadata method) {
            List<String> parameters = new ArrayList<>();
            for (com.example.tests.generator.metadata.ParameterMetadata parameter : method.getParameters()) {
                parameters.add(parameter.getType() + " " + parameter.getName());
            }
            return String.join(", ", parameters);
        }

        private String defaultValue(String returnType) {
            if (returnType == null) {
                return "null";
            }
            String trimmed = returnType.trim();
            return switch (trimmed) {
                case "boolean" -> "false";
                case "byte" -> "(byte) 0";
                case "short" -> "(short) 0";
                case "int" -> "0";
                case "long" -> "0L";
                case "float" -> "0.0f";
                case "double" -> "0.0d";
                case "char" -> "'\\0'";
                case "Boolean" -> "Boolean.FALSE";
                case "Byte" -> "Byte.valueOf((byte) 0)";
                case "Short" -> "Short.valueOf((short) 0)";
                case "Integer" -> "Integer.valueOf(0)";
                case "Long" -> "Long.valueOf(0L)";
                case "Float" -> "Float.valueOf(0.0f)";
                case "Double" -> "Double.valueOf(0.0d)";
                case "Character" -> "Character.valueOf('\\0')";
                case "String" -> "\"\"";
                default -> {
                    if (trimmed.startsWith("Optional") || trimmed.startsWith("java.util.Optional")) {
                        yield "java.util.Optional.empty()";
                    }
                    yield "null";
                }
            };
        }
    }

    private static final class SupportLibraryStubs {
        private static final Map<String, String> SOURCES = createSources();

        private static Map<String, String> createSources() {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("org.junit.jupiter.api.Test", "package org.junit.jupiter.api;\n" +
                    "import java.lang.annotation.ElementType;\n" +
                    "import java.lang.annotation.Retention;\n" +
                    "import java.lang.annotation.RetentionPolicy;\n" +
                    "import java.lang.annotation.Target;\n" +
                    "@Target(ElementType.METHOD)\n" +
                    "@Retention(RetentionPolicy.RUNTIME)\n" +
                    "public @interface Test {}\n");
            sources.put("org.junit.jupiter.api.BeforeEach", "package org.junit.jupiter.api;\n" +
                    "import java.lang.annotation.ElementType;\n" +
                    "import java.lang.annotation.Retention;\n" +
                    "import java.lang.annotation.RetentionPolicy;\n" +
                    "import java.lang.annotation.Target;\n" +
                    "@Target(ElementType.METHOD)\n" +
                    "@Retention(RetentionPolicy.RUNTIME)\n" +
                    "public @interface BeforeEach {}\n");
            sources.put("org.junit.jupiter.api.extension.ExtendWith", "package org.junit.jupiter.api.extension;\n" +
                    "import java.lang.annotation.ElementType;\n" +
                    "import java.lang.annotation.Retention;\n" +
                    "import java.lang.annotation.RetentionPolicy;\n" +
                    "import java.lang.annotation.Target;\n" +
                    "@Target({ElementType.TYPE, ElementType.METHOD})\n" +
                    "@Retention(RetentionPolicy.RUNTIME)\n" +
                    "public @interface ExtendWith { Class<?>[] value(); }\n");
            sources.put("org.junit.jupiter.api.Assertions", "package org.junit.jupiter.api;\n" +
                    "public final class Assertions {\n" +
                    "    private Assertions() {}\n" +
                    "    public static void assertEquals(Object expected, Object actual) {}\n" +
                    "    public static void assertEquals(double expected, double actual, double delta) {}\n" +
                    "    public static void assertEquals(long expected, long actual) {}\n" +
                    "    public static void assertTrue(boolean condition) {}\n" +
                    "    public static void assertFalse(boolean condition) {}\n" +
                    "    public static void assertNotNull(Object value) {}\n" +
                    "    public static <T extends Throwable> T assertThrows(Class<T> expectedType, Executable executable) {\n" +
                    "        try {\n" +
                    "            executable.execute();\n" +
                    "        } catch (Throwable throwable) {\n" +
                    "            return expectedType.cast(throwable);\n" +
                    "        }\n" +
                    "        throw new AssertionError();\n" +
                    "    }\n" +
                    "    public interface Executable { void execute() throws Throwable; }\n" +
                    "}\n");
            sources.put("org.mockito.Mock", "package org.mockito;\n" +
                    "import java.lang.annotation.ElementType;\n" +
                    "import java.lang.annotation.Retention;\n" +
                    "import java.lang.annotation.RetentionPolicy;\n" +
                    "import java.lang.annotation.Target;\n" +
                    "@Target({ElementType.FIELD, ElementType.PARAMETER})\n" +
                    "@Retention(RetentionPolicy.RUNTIME)\n" +
                    "public @interface Mock {}\n");
            sources.put("org.mockito.InjectMocks", "package org.mockito;\n" +
                    "import java.lang.annotation.ElementType;\n" +
                    "import java.lang.annotation.Retention;\n" +
                    "import java.lang.annotation.RetentionPolicy;\n" +
                    "import java.lang.annotation.Target;\n" +
                    "@Target({ElementType.FIELD, ElementType.CONSTRUCTOR})\n" +
                    "@Retention(RetentionPolicy.RUNTIME)\n" +
                    "public @interface InjectMocks {}\n");
            sources.put("org.mockito.junit.jupiter.MockitoExtension", "package org.mockito.junit.jupiter;\n" +
                    "public class MockitoExtension {}\n");
            sources.put("org.mockito.Mockito", "package org.mockito;\n" +
                    "public final class Mockito {\n" +
                    "    private Mockito() {}\n" +
                    "    public static <T> T mock(Class<T> type) { return null; }\n" +
                    "    public static <T> OngoingStubbing<T> when(T invocation) { return new OngoingStubbing<>(); }\n" +
                    "    public static <T> T verify(T mock) { return mock; }\n" +
                    "    public static <T> T verify(T mock, VerificationMode mode) { return mock; }\n" +
                    "    public static <T> T spy(T instance) { return instance; }\n" +
                    "    public static <T> T any() { return null; }\n" +
                    "    public static <T> T any(Class<T> type) { return null; }\n" +
                    "    public static <T> T eq(T value) { return value; }\n" +
                    "    public static VerificationMode times(int wantedNumberOfInvocations) { return new VerificationMode() {}; }\n" +
                    "    public interface VerificationMode {}\n" +
                    "    public static class OngoingStubbing<T> {\n" +
                    "        public OngoingStubbing<T> thenReturn(T value) { return this; }\n" +
                    "    }\n" +
                    "}\n");
            sources.put("org.mockito.BDDMockito", "package org.mockito;\n" +
                    "public final class BDDMockito {\n" +
                    "    private BDDMockito() {}\n" +
                    "    public static <T> Mockito.OngoingStubbing<T> given(T invocation) { return new Mockito.OngoingStubbing<>(); }\n" +
                    "}\n");
            sources.put("org.mockito.ArgumentMatchers", "package org.mockito;\n" +
                    "public final class ArgumentMatchers {\n" +
                    "    private ArgumentMatchers() {}\n" +
                    "    public static <T> T any() { return null; }\n" +
                    "    public static <T> T any(Class<T> type) { return null; }\n" +
                    "    public static <T> T eq(T value) { return value; }\n" +
                    "}\n");
            return sources;
        }

        private static Map<String, String> getSources() {
            return SOURCES;
        }
    }
}
