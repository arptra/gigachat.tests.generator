package com.example.tests.generator.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Captures the documented collaborators for a test along with the auxiliary types referenced by
 * their APIs so prompts can supply exhaustive constructor and method signatures.
 */
public record DependencyDocumentation(Map<String, List<String>> dependencyMethods,
                                      Map<String, List<String>> supportingTypes,
                                      List<String> targetMethods) {

    public DependencyDocumentation {
        dependencyMethods = immutableCopy(dependencyMethods);
        supportingTypes = immutableCopy(supportingTypes);
        targetMethods = List.copyOf(new ArrayList<>(Objects.requireNonNull(targetMethods, "targetMethods")));
    }

    private static Map<String, List<String>> immutableCopy(Map<String, List<String>> source) {
        Objects.requireNonNull(source, "source");
        Map<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, List.copyOf(new ArrayList<>(value))));
        return Map.copyOf(copy);
    }
}

