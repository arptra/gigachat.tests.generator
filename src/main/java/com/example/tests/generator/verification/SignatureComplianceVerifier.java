package com.example.tests.generator.verification;

import com.example.tests.generator.pipeline.GeneratedTestClass;
import com.example.tests.orchestration.gigachat.TestContextSnapshot;
import com.example.tests.orchestration.verification.TestSignatureVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;

/**
 * Delegates signature normalisation to {@link GeneratedTestVerifier} for the given test classes.
 * The verifier rewrites the underlying source files when adjustments are required and returns the
 * updated contents so the orchestration layer can keep its context snapshots in sync.
 */
public final class SignatureComplianceVerifier implements TestSignatureVerifier {

    private final GeneratedTestVerifier delegate;

    public SignatureComplianceVerifier() {
        this(new GeneratedTestVerifier());
    }

    public SignatureComplianceVerifier(GeneratedTestVerifier delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public Map<String, String> verifySignatures(Collection<String> testClassNames,
                                                Map<String, TestContextSnapshot> contexts) {
        Objects.requireNonNull(testClassNames, "testClassNames");
        Objects.requireNonNull(contexts, "contexts");
        Map<String, String> updated = new LinkedHashMap<>();
        LinkedHashSet<String> targets = new LinkedHashSet<>(testClassNames.isEmpty()
                ? contexts.keySet()
                : testClassNames);
        for (String className : targets) {
            TestContextSnapshot context = resolveContext(className, contexts);
            if (context == null) {
                continue;
            }
            Path sourceFile = context.getSourceFile();
            String currentSource = readSource(sourceFile, context.getTestClassName());
            GeneratedTestClass generated = new GeneratedTestClass(
                    packageName(context.getTestClassName()),
                    simpleName(context.getTestClassName()),
                    currentSource
            );
            GeneratedTestClass verified = delegate.verify(generated, null);
            if (!verified.getSourceCode().equals(currentSource)) {
                writeSource(sourceFile, verified.getSourceCode(), context.getTestClassName());
                updated.put(context.getTestClassName(), verified.getSourceCode());
            }
        }
        return updated;
    }

    private TestContextSnapshot resolveContext(String className, Map<String, TestContextSnapshot> contexts) {
        TestContextSnapshot direct = contexts.get(className);
        if (direct != null) {
            return direct;
        }
        String simpleName = simpleName(className);
        TestContextSnapshot match = null;
        for (TestContextSnapshot snapshot : contexts.values()) {
            if (simpleName(snapshot.getTestClassName()).equals(simpleName)) {
                if (match != null) {
                    return null;
                }
                match = snapshot;
            }
        }
        if (match != null) {
            return match;
        }
        return contexts.get(simpleName);
    }

    private String readSource(Path sourceFile, String className) {
        try {
            return Files.readString(sourceFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read generated test for " + className, exception);
        }
    }

    private void writeSource(Path sourceFile, String source, String className) {
        try {
            Files.writeString(sourceFile, source);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist verified test for " + className, exception);
        }
    }

    private String packageName(String className) {
        int lastDot = className.lastIndexOf('.');
        return lastDot >= 0 ? className.substring(0, lastDot) : "";
    }

    private String simpleName(String className) {
        int lastDot = className.lastIndexOf('.');
        return lastDot >= 0 ? className.substring(lastDot + 1) : className;
    }
}
