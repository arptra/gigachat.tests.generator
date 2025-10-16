package com.example.tests.orchestration.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Records a method invocation observed inside the analysed method.
 */
public final class MethodInvocation {

    private final String name;
    private final List<InvocationArgument> arguments;

    public MethodInvocation(String name, List<InvocationArgument> arguments) {
        this.name = Objects.requireNonNull(name, "name");
        this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
    }

    public String getName() {
        return name;
    }

    public List<InvocationArgument> getArguments() {
        return arguments;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MethodInvocation that)) {
            return false;
        }
        return Objects.equals(name, that.name) && Objects.equals(arguments, that.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, arguments);
    }

    @Override
    public String toString() {
        return name + arguments;
    }
}
