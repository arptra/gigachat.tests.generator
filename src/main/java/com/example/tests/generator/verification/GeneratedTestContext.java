package com.example.tests.generator.verification;

import com.example.tests.generator.metadata.ClassMetadata;
import com.example.tests.generator.pipeline.GeneratedTestClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Shared mutable context used by {@link GeneratedTestRule} implementations when
 * applying automated fixes to a generated test class.
 */
public final class GeneratedTestContext {

    private final GeneratedTestClass original;
    private final ClassMetadata metadata;
    private final List<String> compilationErrors;
    private String sourceCode;

    public GeneratedTestContext(GeneratedTestClass original,
                                ClassMetadata metadata,
                                List<String> compilationErrors) {
        this.original = Objects.requireNonNull(original, "original");
        this.metadata = metadata;
        this.sourceCode = original.getSourceCode();
        if (compilationErrors == null || compilationErrors.isEmpty()) {
            this.compilationErrors = List.of();
        } else {
            this.compilationErrors = Collections.unmodifiableList(new ArrayList<>(compilationErrors));
        }
    }

    public GeneratedTestClass getOriginal() {
        return original;
    }

    public Optional<ClassMetadata> getMetadata() {
        return Optional.ofNullable(metadata);
    }

    public List<String> getCompilationErrors() {
        return compilationErrors;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = Objects.requireNonNull(sourceCode, "sourceCode");
    }
}
