package com.example.tests.generator.validate;

import com.example.tests.generator.pipeline.GeneratedTestClass;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import javax.lang.model.element.Modifier;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Converts validated Java source into {@link GeneratedTestClass} instances.
 */
public class TestCodeParser {

    private final JavaCompiler compiler;

    public TestCodeParser() {
        this.compiler = ToolProvider.getSystemJavaCompiler();
        if (this.compiler == null) {
            throw new IllegalStateException("Java compiler is not available. Ensure a JDK is installed.");
        }
    }

    public Optional<GeneratedTestClass> parse(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        InMemoryJavaFile file = new InMemoryJavaFile("GeneratedTest", code);
        JavacTask task = (JavacTask) compiler.getTask(null, null, diagnostics, List.of("-proc:none"), null, List.of(file));
        List<CompilationUnitTree> units = new ArrayList<>();
        try {
            for (CompilationUnitTree unit : task.parse()) {
                units.add(unit);
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
        GeneratedTestClass firstCandidate = null;
        GeneratedTestClass testNamedCandidate = null;
        for (CompilationUnitTree unit : units) {
            String packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
            for (var type : unit.getTypeDecls()) {
                if (type instanceof ClassTree classTree && classTree.getKind() == Tree.Kind.CLASS) {
                    GeneratedTestClass candidate = new GeneratedTestClass(packageName,
                            classTree.getSimpleName().toString(), code);
                    if (classTree.getModifiers().getFlags().contains(Modifier.PUBLIC)) {
                        return Optional.of(candidate);
                    }
                    if (testNamedCandidate == null && classTree.getSimpleName().toString().endsWith("Test")) {
                        testNamedCandidate = candidate;
                    }
                    if (firstCandidate == null) {
                        firstCandidate = candidate;
                    }
                }
            }
        }
        if (testNamedCandidate != null) {
            return Optional.of(testNamedCandidate);
        }
        return Optional.ofNullable(firstCandidate);
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
}
