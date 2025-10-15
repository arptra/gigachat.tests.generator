package com.example.tests.generator.verification;

import com.example.tests.generator.metadata.ClassMetadata;
import com.example.tests.generator.pipeline.GeneratedTestClass;
import com.example.tests.generator.verification.rules.DependencyFieldCleanupRule;
import com.example.tests.generator.verification.rules.ImportSanitizerRule;
import com.example.tests.generator.verification.rules.MethodSignatureNormalizationRule;
import com.example.tests.generator.verification.rules.MockitoCompilationFixRule;
import com.example.tests.generator.verification.rules.ReturnTypeCorrectionRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Applies a sequence of {@link GeneratedTestRule rules} to a generated test
 * class. The verifier is intentionally simple so that new rules can be added in
 * the future without touching existing call sites.
 */
public final class GeneratedTestVerifier {

    private final List<GeneratedTestRule> rules;

    public GeneratedTestVerifier() {
        this(List.of(
                new MockitoCompilationFixRule(),
                new ImportSanitizerRule(),
                new MethodSignatureNormalizationRule(),
                new DependencyFieldCleanupRule(),
                new ReturnTypeCorrectionRule()
        ));
    }

    public GeneratedTestVerifier(List<GeneratedTestRule> rules) {
        Objects.requireNonNull(rules, "rules");
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("rules must not be empty");
        }
        this.rules = List.copyOf(rules);
    }

    public GeneratedTestClass verify(GeneratedTestClass generatedTest, ClassMetadata metadata) {
        return verify(generatedTest, metadata, List.of());
    }

    public GeneratedTestClass verify(GeneratedTestClass generatedTest,
                                     ClassMetadata metadata,
                                     List<String> compilationErrors) {
        Objects.requireNonNull(generatedTest, "generatedTest");
        GeneratedTestContext context = new GeneratedTestContext(generatedTest, metadata, compilationErrors);
        for (GeneratedTestRule rule : rules) {
            rule.apply(context);
        }
        return new GeneratedTestClass(
                generatedTest.getPackageName(),
                generatedTest.getClassName(),
                context.getSourceCode()
        );
    }

    public List<GeneratedTestRule> getRules() {
        return new ArrayList<>(rules);
    }
}
