package com.example.tests.generator.method;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
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

/**
 * Parses a production class and extracts logical representations of methods that
 * require unit tests.
 */
public class TargetClassAnalyzer {

    private final JavaCompiler compiler;

    public TargetClassAnalyzer() {
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
        InMemoryJavaFile file = new InMemoryJavaFile("AnalyzedClass", code);
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
                    extractTargetMethods(classTree).stream()
                            .map(method -> toAnalysis(method, packageName, className))
                            .flatMap(Optional::stream)
                            .forEach(analyses::add);
                }
            }
        }
        return List.copyOf(analyses);
    }

    private List<MethodTree> extractTargetMethods(ClassTree classTree) {
        List<MethodTree> methods = new ArrayList<>();
        for (var member : classTree.getMembers()) {
            if (member instanceof MethodTree methodTree && isCandidate(methodTree)) {
                methods.add(methodTree);
            }
        }
        return methods;
    }

    private boolean isCandidate(MethodTree methodTree) {
        if (methodTree.getBody() == null) {
            return false;
        }
        if ("<init>".equals(methodTree.getName().toString())) {
            return false;
        }
        String modifiers = methodTree.getModifiers().toString();
        return !modifiers.contains("private");
    }

    private Optional<MethodAnalysis> toAnalysis(MethodTree method,
                                                String packageName,
                                                String className) {
        BlockTree body = method.getBody();
        if (body == null) {
            return Optional.empty();
        }
        List<LogicalCodeUnit> units = extractUnits(body.toString());
        String methodSource = method.toString();
        String fullyQualifiedClass = packageName.isBlank() ? className : packageName + '.' + className;
        return Optional.of(new MethodAnalysis(fullyQualifiedClass, method.getName().toString(), methodSource, units));
    }

    private List<LogicalCodeUnit> extractUnits(String blockSource) {
        String trimmed = blockSource == null ? "" : blockSource.trim();
        if (trimmed.startsWith("{")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("}")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        List<LogicalCodeUnit> units = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int braceDepth = 0;
        int index = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            current.append(ch);
            if (ch == '{') {
                braceDepth++;
            } else if (ch == '}') {
                braceDepth--;
                if (braceDepth == 0) {
                    addUnit(units, current, index++);
                }
            } else if (ch == ';' && braceDepth == 0) {
                addUnit(units, current, index++);
            }
        }
        addUnit(units, current, index);
        return units;
    }

    private void addUnit(List<LogicalCodeUnit> units, StringBuilder current, int index) {
        if (current.length() == 0) {
            return;
        }
        String candidate = current.toString().trim();
        if (!candidate.isEmpty()) {
            units.add(new LogicalCodeUnit(index, candidate));
        }
        current.setLength(0);
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
