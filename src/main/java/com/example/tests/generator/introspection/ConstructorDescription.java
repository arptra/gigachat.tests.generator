package com.example.tests.generator.introspection;

import java.util.List;
import java.util.Objects;

/**
 * Describes a constructor that was discovered during dependency introspection.
 */
public record ConstructorDescription(
        String declaringClass,
        String name,
        List<String> modifiers,
        List<String> parameterTypes,
        List<String> parameterNames,
        List<String> exceptionTypes,
        List<String> annotations,
        boolean varArgs
) {

    public ConstructorDescription {
        Objects.requireNonNull(declaringClass, "declaringClass");
        Objects.requireNonNull(name, "name");
        modifiers = List.copyOf(modifiers);
        parameterTypes = List.copyOf(parameterTypes);
        parameterNames = List.copyOf(parameterNames);
        exceptionTypes = List.copyOf(exceptionTypes);
        annotations = List.copyOf(annotations);
    }

    /**
     * Returns a human-readable representation of the constructor signature.
     */
    public String signature() {
        String params = IntrospectionFormatter.joinParameters(parameterTypes, parameterNames, varArgs);
        String throwsPart = exceptionTypes.isEmpty()
                ? ""
                : " throws " + String.join(", ", exceptionTypes);
        String modifiersPart = modifiers.isEmpty()
                ? ""
                : String.join(" ", modifiers) + " ";
        return modifiersPart + name + "(" + params + ")" + throwsPart;
    }

    /**
     * Unique signature used for sorting and equality comparisons.
     */
    public String qualifiedSignature() {
        return declaringClass + "#" + name + "(" + String.join(",", parameterTypes) + ")";
    }
}
