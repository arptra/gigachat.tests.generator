package com.example.tests.generator.introspection;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Aggregated description of a class including its constructors and methods.
 */
public record ClassIntrospectionResult(
        String className,
        String packageName,
        String superClassName,
        List<String> interfaceNames,
        List<String> annotations,
        List<ConstructorDescription> constructors,
        List<MethodDescription> methods
) {

    public ClassIntrospectionResult {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(packageName, "packageName");
        Objects.requireNonNull(interfaceNames, "interfaceNames");
        Objects.requireNonNull(annotations, "annotations");
        Objects.requireNonNull(constructors, "constructors");
        Objects.requireNonNull(methods, "methods");
        interfaceNames = List.copyOf(interfaceNames);
        annotations = List.copyOf(annotations);
        constructors = List.copyOf(constructors);
        methods = List.copyOf(methods);
    }

    /**
     * Creates a textual representation of the collected information suitable for prompt usage.
     */
    public String formatAsText() {
        StringBuilder builder = new StringBuilder();
        builder.append("Class: ").append(className);
        if (!packageName.isEmpty()) {
            builder.append(" (package ").append(packageName).append(")");
        }
        builder.append(System.lineSeparator());
        if (superClassName != null && !superClassName.equals(Object.class.getName())) {
            builder.append("  Extends: ").append(superClassName).append(System.lineSeparator());
        }
        if (!interfaceNames.isEmpty()) {
            builder.append("  Implements: ").append(String.join(", ", interfaceNames)).append(System.lineSeparator());
        }
        if (!annotations.isEmpty()) {
            builder.append("  Annotations: ").append(String.join(", ", annotations)).append(System.lineSeparator());
        }
        if (!constructors.isEmpty()) {
            builder.append("  Constructors:").append(System.lineSeparator());
            for (ConstructorDescription constructor : constructors) {
                builder.append("    - ").append(constructor.signature()).append(System.lineSeparator());
            }
        }
        if (!methods.isEmpty()) {
            builder.append("  Methods:").append(System.lineSeparator());
            for (MethodDescription method : methods) {
                builder.append("    - ").append(method.signature()).append(System.lineSeparator());
            }
        }
        return builder.toString();
    }

    /**
     * Combines constructor and method signatures into a single list.
     */
    public List<String> allSignatures() {
        List<String> signatures = new ArrayList<>(constructors.size() + methods.size());
        constructors.stream().map(ConstructorDescription::signature).forEach(signatures::add);
        methods.stream().map(MethodDescription::signature).forEach(signatures::add);
        return signatures;
    }
}
