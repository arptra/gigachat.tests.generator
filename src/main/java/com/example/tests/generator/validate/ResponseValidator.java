package com.example.tests.generator.validate;

import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.JavacTask;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Validates that the response returned by the agent contains a compilable Java test class.
 */
public class ResponseValidator {

    private static final List<String> REQUIRED_IMPORT_PREFIXES = List.of("org.junit.jupiter", "org.mockito");
    private static final List<String> DISALLOWED_IMPORT_PREFIXES = List.of("org.assertj");

    private final JavaCompiler compiler;

    public ResponseValidator() {
        this.compiler = ToolProvider.getSystemJavaCompiler();
        if (this.compiler == null) {
            throw new IllegalStateException("Java compiler is not available. Ensure a JDK is installed.");
        }
    }

    public ValidationResult validate(String response) {
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

    private String annotationSimpleName(AnnotationTree annotation) {
        return annotation.getAnnotationType().toString().replaceAll("^.*\\.", "");
    }

    private Optional<String> importQualifiedName(ImportTree importTree) {
        return Optional.ofNullable(importTree.getQualifiedIdentifier()).map(Object::toString);
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
}
