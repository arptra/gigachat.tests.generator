package com.example.tests.generator.method;

import java.util.List;
import java.util.Optional;

/**
 * Validates generated mock plans using an external system (e.g. Gigachat).
 */
public interface MethodMockValidationService {

    Optional<String> validate(MethodAnalysis analysis, List<MockSnippet> snippets);
}
