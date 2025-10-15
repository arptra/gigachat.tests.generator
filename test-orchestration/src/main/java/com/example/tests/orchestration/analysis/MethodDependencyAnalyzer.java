package com.example.tests.orchestration.analysis;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Analyses Java source files to build dependency graphs for individual methods.
 */
public final class MethodDependencyAnalyzer {

    private static final List<String> JAVAC_OPTIONS = List.of("-proc:none");

    public MethodDependencyGraph analyze(Path sourceFile, String fullyQualifiedClassName, String methodName) {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(fullyQualifiedClassName, "fullyQualifiedClassName");
        Objects.requireNonNull(methodName, "methodName");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is not available in the current runtime");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> sources = fileManager.getJavaFileObjects(sourceFile.toFile());
            JavacTask task = (JavacTask) compiler.getTask(null, fileManager, diagnostics, JAVAC_OPTIONS, null, sources);
            List<CompilationUnitTree> units = new ArrayList<>();
            for (CompilationUnitTree unit : task.parse()) {
                units.add(unit);
            }

            List<String> errors = diagnostics.getDiagnostics().stream()
                    .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                    .map(diagnostic -> formatDiagnostic(diagnostic))
                    .toList();
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException("Failed to parse " + sourceFile + ": " + errors);
            }

            MethodLookup lookup = new MethodLookup(fullyQualifiedClassName, methodName);
            for (CompilationUnitTree unit : units) {
                lookup.scan(unit, null);
                if (lookup.getMethod() != null) {
                    break;
                }
            }

            MethodTree methodTree = lookup.getMethod();
            TreePath methodPath = lookup.getMethodPath();
            if (methodTree == null) {
                throw new IllegalArgumentException(String.format(Locale.ENGLISH,
                        "Method %s.%s not found in %s",
                        fullyQualifiedClassName, methodName, sourceFile));
            }
            if (methodPath == null) {
                throw new IllegalStateException(String.format(Locale.ENGLISH,
                        "Failed to resolve tree path for %s.%s in %s",
                        fullyQualifiedClassName, methodName, sourceFile));
            }

            MethodDependencyExtractor extractor = new MethodDependencyExtractor(
                    fullyQualifiedClassName,
                    methodTree,
                    methodPath);
            return extractor.extract();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + sourceFile, e);
        }
    }

    private String formatDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic) {
        JavaFileObject source = diagnostic.getSource();
        String location = source == null ? "" : source.getName();
        return String.format(Locale.ENGLISH, "%s:%d %s", location, diagnostic.getLineNumber(),
                diagnostic.getMessage(Locale.ENGLISH));
    }

    private static final class MethodLookup extends TreePathScanner<Void, Void> {

        private final String targetClass;
        private final String targetMethod;
        private MethodTree method;
        private TreePath methodPath;

        private MethodLookup(String targetClass, String targetMethod) {
            this.targetClass = targetClass;
            this.targetMethod = targetMethod;
        }

        @Override
        public Void visitMethod(MethodTree node, Void unused) {
            if (method != null) {
                return null;
            }
            if (!node.getName().contentEquals(targetMethod)) {
                return super.visitMethod(node, unused);
            }
            TreePath path = getCurrentPath();
            if (path == null) {
                return super.visitMethod(node, unused);
            }

            String fqcn = resolveClassName(path);
            if (targetClass.equals(fqcn)) {
                if (method != null) {
                    throw new IllegalArgumentException(String.format(Locale.ENGLISH,
                            "Multiple methods named %s found in %s", targetMethod, targetClass));
                }
                method = node;
                methodPath = getCurrentPath();
            }
            return super.visitMethod(node, unused);
        }

        private String resolveClassName(TreePath methodPath) {
            Deque<String> segments = new ArrayDeque<>();
            TreePath current = methodPath;
            while (current != null) {
                Tree leaf = current.getLeaf();
                if (leaf instanceof ClassTree classTree) {
                    segments.addFirst(classTree.getSimpleName().toString());
                }
                current = current.getParentPath();
            }
            String simpleName = String.join(".", segments);
            CompilationUnitTree unit = methodPath.getCompilationUnit();
            String packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
            return packageName.isEmpty() ? simpleName : packageName + '.' + simpleName;
        }

        private MethodTree getMethod() {
            return method;
        }

        private TreePath getMethodPath() {
            return methodPath;
        }

    }

    private static final class MethodDependencyExtractor extends TreePathScanner<Void, CreationContext> {

        private final String fullyQualifiedClassName;
        private final MethodTree methodTree;
        private final TreePath methodPath;

        private final Map<String, String> variableTypes = new LinkedHashMap<>();
        private final Map<NewClassTree, DependencyNode> dependenciesByTree = new IdentityHashMap<>();
        private final Map<String, DependencyNode> dependenciesByVariable = new LinkedHashMap<>();
        private final List<DependencyNode> rootDependencies = new ArrayList<>();
        private final List<MethodInvocation> unattachedInvocations = new ArrayList<>();

        private MethodDependencyExtractor(String fullyQualifiedClassName,
                                          MethodTree methodTree,
                                          TreePath methodPath) {
            this.fullyQualifiedClassName = fullyQualifiedClassName;
            this.methodTree = methodTree;
            this.methodPath = methodPath;
        }

        private MethodDependencyGraph extract() {
            if (methodTree.getParameters() != null) {
                methodTree.getParameters().forEach(parameter ->
                        variableTypes.put(parameter.getName().toString(), parameter.getType().toString()));
            }

            BlockTree body = methodTree.getBody();
            if (body != null) {
                scan(new TreePath(methodPath, body), null);
            }

            List<MethodParameter> parameters = methodTree.getParameters() == null
                    ? List.of()
                    : methodTree.getParameters().stream()
                    .map(parameter -> new MethodParameter(parameter.getName().toString(), parameter.getType().toString()))
                    .toList();

            return new MethodDependencyGraph(
                    fullyQualifiedClassName,
                    methodTree.getName().toString(),
                    parameters,
                    rootDependencies,
                    unattachedInvocations
            );
        }

        @Override
        public Void visitVariable(VariableTree node, CreationContext context) {
            String name = node.getName().toString();
            String declaredType = node.getType() == null ? null : node.getType().toString();
            variableTypes.put(name, declaredType == null ? "var" : declaredType);
            if (node.getInitializer() != null) {
                scan(node.getInitializer(), new CreationContext(null, name, declaredType, DependencyOrigin.LOCAL_VARIABLE));
            }
            return null;
        }

        @Override
        public Void visitAssignment(AssignmentTree node, CreationContext context) {
            String variableName = extractVariableName(node.getVariable());
            if (variableName != null) {
                String declaredType = variableTypes.get(variableName);
                scan(node.getExpression(), new CreationContext(null, variableName, declaredType, DependencyOrigin.LOCAL_VARIABLE));
            } else {
                scan(node.getVariable(), context);
                scan(node.getExpression(), context);
            }
            return null;
        }

        @Override
        public Void visitReturn(ReturnTree node, CreationContext context) {
            if (node.getExpression() != null) {
                scan(node.getExpression(), new CreationContext(null, null, null, DependencyOrigin.RETURN_VALUE));
            }
            return null;
        }

        @Override
        public Void visitIf(IfTree node, CreationContext context) {
            scan(node.getCondition(), context);
            scan(node.getThenStatement(), context);
            if (node.getElseStatement() != null) {
                scan(node.getElseStatement(), context);
            }
            return null;
        }

        @Override
        public Void visitTry(TryTree node, CreationContext context) {
            scan(node.getBlock(), context);
            node.getCatches().forEach(catchTree -> scan(catchTree, context));
            if (node.getFinallyBlock() != null) {
                scan(node.getFinallyBlock(), context);
            }
            return null;
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, CreationContext context) {
            scan(node.getMethodSelect(), new CreationContext(null, null, null, DependencyOrigin.INLINE_TARGET));
            DependencyNode target = resolveTarget(node.getMethodSelect());
            MethodInvocation invocation = buildMethodInvocation(node);
            if (target != null) {
                target.addMethodInvocation(invocation);
            } else {
                unattachedInvocations.add(invocation);
            }

            for (ExpressionTree argument : node.getArguments()) {
                linkExistingDependency(target, argument);
                scan(argument, new CreationContext(target, null, null, DependencyOrigin.METHOD_ARGUMENT));
            }
            return null;
        }

        @Override
        public Void visitNewClass(NewClassTree node, CreationContext context) {
            String typeName = node.getIdentifier().toString();
            String variableName = context == null ? null : context.variableName;
            String declaredType = context == null ? null : context.declaredType;
            DependencyOrigin origin = context == null ? DependencyOrigin.UNKNOWN : context.origin;

            DependencyNode dependency = new DependencyNode(typeName, variableName, declaredType, origin);

            if (context != null && context.parent != null) {
                context.parent.addDependency(dependency);
            } else {
                rootDependencies.add(dependency);
            }

            if (variableName != null) {
                dependenciesByVariable.put(variableName, dependency);
                variableTypes.put(variableName, typeName);
            }
            dependenciesByTree.put(node, dependency);

            List<? extends ExpressionTree> arguments = node.getArguments();
            for (ExpressionTree argument : arguments) {
                InvocationArgument invocationArgument = buildArgument(argument);
                dependency.addConstructorArgument(invocationArgument);
                linkExistingDependency(dependency, argument);
            }

            for (ExpressionTree argument : arguments) {
                scan(argument, new CreationContext(dependency, null, null, DependencyOrigin.CONSTRUCTOR_ARGUMENT));
            }

            if (node.getEnclosingExpression() != null) {
                scan(node.getEnclosingExpression(), new CreationContext(dependency, null, null, DependencyOrigin.CONSTRUCTOR_ARGUMENT));
            }
            if (node.getClassBody() != null) {
                scan(node.getClassBody(), context);
            }
            return null;
        }

        private void linkExistingDependency(DependencyNode owner, ExpressionTree argument) {
            if (owner == null) {
                return;
            }
            DependencyNode referenced = resolveDependencyReference(argument);
            owner.addDependency(referenced);
        }

        private DependencyNode resolveDependencyReference(ExpressionTree expression) {
            ExpressionTree unwrapped = unwrap(expression);
            if (unwrapped instanceof IdentifierTree identifier) {
                return dependenciesByVariable.get(identifier.getName().toString());
            }
            if (unwrapped instanceof MemberSelectTree memberSelect) {
                ExpressionTree expressionTree = unwrap(memberSelect.getExpression());
                if (expressionTree instanceof IdentifierTree identifierTree) {
                    return dependenciesByVariable.get(identifierTree.getName().toString());
                }
            }
            return null;
        }

        private MethodInvocation buildMethodInvocation(MethodInvocationTree node) {
            String methodName = extractMethodName(node.getMethodSelect());
            List<InvocationArgument> arguments = node.getArguments().stream()
                    .map(this::buildArgument)
                    .toList();
            return new MethodInvocation(methodName, arguments);
        }

        private String extractMethodName(ExpressionTree methodSelect) {
            ExpressionTree unwrapped = unwrap(methodSelect);
            if (unwrapped instanceof MemberSelectTree memberSelect) {
                return memberSelect.getIdentifier().toString();
            }
            if (unwrapped instanceof IdentifierTree identifier) {
                return identifier.getName().toString();
            }
            if (unwrapped instanceof MemberReferenceTree referenceTree) {
                return referenceTree.getName().toString();
            }
            return unwrapped.toString();
        }

        private InvocationArgument buildArgument(ExpressionTree expression) {
            String inferredType = inferType(expression);
            return new InvocationArgument(inferredType, expression.toString());
        }

        private String inferType(ExpressionTree expression) {
            ExpressionTree unwrapped = unwrap(expression);
            return switch (unwrapped.getKind()) {
                case STRING_LITERAL -> "String";
                case CHAR_LITERAL -> "char";
                case BOOLEAN_LITERAL -> "boolean";
                case INT_LITERAL -> "int";
                case LONG_LITERAL -> "long";
                case FLOAT_LITERAL -> "float";
                case DOUBLE_LITERAL -> "double";
                case NULL_LITERAL -> "null";
                default -> inferComplexType(unwrapped);
            };
        }

        private String inferComplexType(ExpressionTree expression) {
            if (expression instanceof NewClassTree newClassTree) {
                return newClassTree.getIdentifier().toString();
            }
            if (expression instanceof IdentifierTree identifierTree) {
                return variableTypes.getOrDefault(identifierTree.getName().toString(), "Unknown");
            }
            if (expression instanceof MemberSelectTree memberSelectTree) {
                ExpressionTree target = unwrap(memberSelectTree.getExpression());
                if (target instanceof IdentifierTree identifierTree) {
                    String identifier = identifierTree.getName().toString();
                    String resolved = variableTypes.get(identifier);
                    return resolved == null ? identifier : resolved;
                }
                return memberSelectTree.getIdentifier().toString();
            }
            if (expression instanceof MethodInvocationTree invocationTree) {
                return extractMethodName(invocationTree.getMethodSelect()) + "()";
            }
            return expression.getKind().toString();
        }

        private DependencyNode resolveTarget(ExpressionTree select) {
            ExpressionTree unwrapped = unwrap(select);
            if (unwrapped instanceof MethodInvocationTree invocationTree) {
                // Nested method invocation – attempt to resolve the outermost target
                return resolveTarget(invocationTree.getMethodSelect());
            }
            if (unwrapped instanceof MemberSelectTree memberSelectTree) {
                DependencyNode resolved = resolveTarget(memberSelectTree.getExpression());
                if (resolved != null) {
                    return resolved;
                }
                ExpressionTree expressionTree = unwrap(memberSelectTree.getExpression());
                if (expressionTree instanceof IdentifierTree identifierTree) {
                    return dependenciesByVariable.get(identifierTree.getName().toString());
                }
                if (expressionTree instanceof NewClassTree newClassTree) {
                    return dependenciesByTree.get(newClassTree);
                }
                return dependenciesByTree.get(memberSelectTree.getExpression());
            }
            if (unwrapped instanceof IdentifierTree identifierTree) {
                return dependenciesByVariable.get(identifierTree.getName().toString());
            }
            if (unwrapped instanceof NewClassTree newClassTree) {
                return dependenciesByTree.get(newClassTree);
            }
            return null;
        }

        private String extractVariableName(ExpressionTree expression) {
            ExpressionTree unwrapped = unwrap(expression);
            if (unwrapped instanceof IdentifierTree identifierTree) {
                return identifierTree.getName().toString();
            }
            if (unwrapped instanceof MemberSelectTree memberSelectTree) {
                ExpressionTree target = unwrap(memberSelectTree.getExpression());
                if (target instanceof IdentifierTree identifierTree) {
                    return identifierTree.getName().toString();
                }
            }
            return null;
        }

        private ExpressionTree unwrap(ExpressionTree expression) {
            ExpressionTree current = expression;
            while (current instanceof ParenthesizedTree parenthesizedTree) {
                current = parenthesizedTree.getExpression();
            }
            return current;
        }
    }

    private static final class CreationContext {
        private final DependencyNode parent;
        private final String variableName;
        private final String declaredType;
        private final DependencyOrigin origin;

        private CreationContext(DependencyNode parent,
                                String variableName,
                                String declaredType,
                                DependencyOrigin origin) {
            this.parent = parent;
            this.variableName = variableName;
            this.declaredType = declaredType;
            this.origin = origin;
        }
    }
}
