package com.example.tests.generator.method.rules;

import com.example.tests.generator.method.LogicalCodeUnit;
import com.example.tests.generator.method.MethodAnalysisContext;
import com.example.tests.generator.method.MockSnippet;
import com.example.tests.generator.method.MockTemplateRepository;

import java.util.Optional;

/**
 * Contract for producing mock snippets from logical code units.
 */
public interface MockRule {

    Optional<MockSnippet> apply(LogicalCodeUnit unit,
                                MethodAnalysisContext context,
                                MockTemplateRepository templates);

    String id();
}
