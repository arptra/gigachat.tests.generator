package com.example.tests.generator.introspection;

import java.util.List;
import java.util.Objects;

/**
 * Describes a method that was discovered during dependency introspection.
 */
public record MethodDescription(
        String declaringClass,
        String name,
        String returnType,
        List<String> modifiers,
        List<String> parameterTypes,
        List<String> parameterNames,
        List<String> exceptionTypes,
        List<String> annotations,
        boolean defaultMethod,
        boolean varArgs
) {

    public MethodDescription {
        Objects.requireNonNull(declaringClass, "declaringClass");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(returnType, "returnType");
        modifiers = List.copyOf(modifiers);
        parameterTypes = List.copyOf(parameterTypes);
        parameterNames = List.copyOf(parameterNames);
        exceptionTypes = List.copyOf(exceptionTypes);
        annotations = List.copyOf(annotations);
    }

    /**
     * Returns a unique signature identifying the method by declaring class and parameter types.
     */
    public String qualifiedSignature() {
        return declaringClass + "#" + name + "(" + String.join(",", parameterTypes) + ")";
    }

    /**
     * Returns a human-readable signature that can be embedded into prompts.
     */
    public String signature() {
        String modifiersPart = modifiers.isEmpty()
                ? ""
                : String.join(" ", modifiers) + " ";
        String params = IntrospectionFormatter.joinParameters(parameterTypes, parameterNames, varArgs);
        String throwsPart = exceptionTypes.isEmpty()
                ? ""
                : " throws " + String.join(", ", exceptionTypes);
        return modifiersPart + returnType + " " + name + "(" + params + ")" + throwsPart;
    }
}
