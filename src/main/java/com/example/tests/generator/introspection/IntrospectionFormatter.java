package com.example.tests.generator.introspection;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class IntrospectionFormatter {

    private IntrospectionFormatter() {
    }

    static String joinParameters(List<String> parameterTypes, List<String> parameterNames, boolean varArgs) {
        Objects.requireNonNull(parameterTypes, "parameterTypes");
        Objects.requireNonNull(parameterNames, "parameterNames");

        List<String> parts = new ArrayList<>(parameterTypes.size());
        for (int i = 0; i < parameterTypes.size(); i++) {
            String type = parameterTypes.get(i);
            if (varArgs && i == parameterTypes.size() - 1) {
                type = type.replace("[]", "...");
            }
            String name = parameterNames.size() > i ? parameterNames.get(i) : "arg" + i;
            parts.add(type + " " + name);
        }
        return String.join(", ", parts);
    }
}
