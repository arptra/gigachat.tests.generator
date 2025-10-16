package com.example.tests.orchestration.verification;

import com.example.tests.orchestration.gigachat.TestContextSnapshot;

import java.util.Collection;
import java.util.Map;

/**
 * Provides a hook to validate and normalise generated test sources after a successful
 * compilation run. Implementations may rewrite source files to ensure their method
 * signatures comply with project expectations and return the updated source code for
 * downstream bookkeeping.
 */
public interface TestSignatureVerifier {

    /**
     * Verifies the provided test classes, returning a mapping of fully qualified class
     * names to updated source contents when adjustments are required. Implementations
     * may return an empty map when no changes are needed.
     *
     * @param testClassNames the collection of test class names that triggered verification
     * @param contexts       the known contexts for generated tests keyed by class name
     * @return a mapping of fully qualified class names to updated source code
     */
    Map<String, String> verifySignatures(Collection<String> testClassNames,
                                         Map<String, TestContextSnapshot> contexts);
}
