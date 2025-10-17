package com.example.tests.generator.method;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.util.JavacTask;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Parses Java source code and extracts logical representations of test methods.
 */
public class TestMethodAnalyzer {

    private static final Set<String> TEST_ANNOTATIONS = Set.of(
            "Test",
            "ParameterizedTest",
            "RepeatedTest",
            "TestFactory",
            "TestTemplate"
    );

    private final JavaCompiler compiler;

    public TestMethodAnalyzer() {
        this.compiler = ToolProvider.getSystemJavaCompiler();
        if (this.compiler == null) {
            throw new IllegalStateException("Java compiler is not available. Ensure a JDK is installed.");
        }
    }

    public List<MethodAnalysis> analyze(String code) {
        if (code == null || code.isBlank()) {
            return List.of();
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        InMemoryJavaFile file = new InMemoryJavaFile("AnalyzedTest", code);
        JavacTask task = (JavacTask) compiler.getTask(null, null, diagnostics, List.of("-proc:none"), null, List.of(file));
        List<CompilationUnitTree> units = new ArrayList<>();
        try {
            for (CompilationUnitTree unit : task.parse()) {
                units.add(unit);
            }
        } catch (Exception ignored) {
            return List.of();
        }
        List<MethodAnalysis> analyses = new ArrayList<>();
        for (CompilationUnitTree unit : units) {
            String packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
            for (var type : unit.getTypeDecls()) {
                if (type instanceof ClassTree classTree) {
                    String className = classTree.getSimpleName().toString();
                    extractTestMethods(classTree).stream()
                            .map(method -> toAnalysis(method, packageName, className))
                            .flatMap(Optional::stream)
                            .forEach(analyses::add);
                }
            }
        }
        return List.copyOf(analyses);
    }

    private List<MethodTree> extractTestMethods(ClassTree classTree) {
        List<MethodTree> methods = new ArrayList<>();
        for (var member : classTree.getMembers()) {
            if (member instanceof MethodTree methodTree && isTestMethod(methodTree)) {
                methods.add(methodTree);
            }
        }
        return methods;
    }

    private boolean isTestMethod(MethodTree methodTree) {
        if (methodTree.getBody() == null) {
            return false;
        }
        if (!methodTree.getModifiers().getAnnotations().isEmpty()) {
            for (AnnotationTree annotation : methodTree.getModifiers().getAnnotations()) {
                String simpleName = extractSimpleName(annotation.getAnnotationType().toString());
                if (TEST_ANNOTATIONS.contains(simpleName)) {
                    return true;
                }
            }
        }
        return methodTree.getName().toString().startsWith("test");
    }

    private Optional<MethodAnalysis> toAnalysis(MethodTree method,
                                                String packageName,
                                                String className) {
        BlockTree body = method.getBody();
        if (body == null) {
            return Optional.empty();
        }
        List<LogicalCodeUnit> units = new ArrayList<>();
        int index = 0;
        for (StatementTree statement : body.getStatements()) {
            String source = statement.toString().trim();
            if (!source.isEmpty()) {
                units.add(new LogicalCodeUnit(index++, source));
            }
        }
        String methodSource = method.toString();
        String fullyQualifiedClass = packageName.isBlank() ? className : packageName + '.' + className;
        return Optional.of(new MethodAnalysis(fullyQualifiedClass, method.getName().toString(), methodSource, units));
    }

    private String extractSimpleName(String qualified) {
        int lastDot = qualified.lastIndexOf('.');
        return lastDot >= 0 ? qualified.substring(lastDot + 1) : qualified;
    }

    private static class InMemoryJavaFile extends SimpleJavaFileObject {
        private final String code;

        private InMemoryJavaFile(String className, String code) {
            super(URI.create("string:///" + className.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension),
                    JavaFileObject.Kind.SOURCE);
            this.code = Objects.requireNonNull(code, "code");
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
