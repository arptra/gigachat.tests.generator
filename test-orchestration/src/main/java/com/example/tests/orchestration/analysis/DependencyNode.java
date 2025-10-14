package com.example.tests.orchestration.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents an instantiated object within the analysed method along with its
 * constructor arguments, method invocations, and nested dependencies.
 */
public final class DependencyNode {

    private final String type;
    private final String variableName;
    private final String declaredType;
    private final DependencyOrigin origin;
    private final List<InvocationArgument> constructorArguments = new ArrayList<>();
    private final List<MethodInvocation> methodInvocations = new ArrayList<>();
    private final List<DependencyNode> dependencies = new ArrayList<>();

    public DependencyNode(String type, String variableName, String declaredType, DependencyOrigin origin) {
        this.type = Objects.requireNonNull(type, "type");
        this.variableName = variableName;
        this.declaredType = declaredType == null ? type : declaredType;
        this.origin = origin == null ? DependencyOrigin.UNKNOWN : origin;
    }

    public String getType() {
        return type;
    }

    public String getVariableName() {
        return variableName;
    }

    public String getDeclaredType() {
        return declaredType;
    }

    public DependencyOrigin getOrigin() {
        return origin;
    }

    public void addConstructorArgument(InvocationArgument argument) {
        constructorArguments.add(Objects.requireNonNull(argument, "argument"));
    }

    public void addMethodInvocation(MethodInvocation invocation) {
        methodInvocations.add(Objects.requireNonNull(invocation, "invocation"));
    }

    public void addDependency(DependencyNode dependency) {
        if (dependency == null) {
            return;
        }
        boolean alreadyPresent = dependencies.stream().anyMatch(existing -> existing == dependency);
        if (!alreadyPresent) {
            dependencies.add(dependency);
        }
    }

    public List<InvocationArgument> getConstructorArguments() {
        return Collections.unmodifiableList(constructorArguments);
    }

    public List<MethodInvocation> getMethodInvocations() {
        return Collections.unmodifiableList(methodInvocations);
    }

    public List<DependencyNode> getDependencies() {
        return Collections.unmodifiableList(dependencies);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DependencyNode that)) {
            return false;
        }
        return Objects.equals(type, that.type)
                && Objects.equals(variableName, that.variableName)
                && Objects.equals(declaredType, that.declaredType)
                && origin == that.origin
                && Objects.equals(constructorArguments, that.constructorArguments)
                && Objects.equals(methodInvocations, that.methodInvocations)
                && Objects.equals(dependencies, that.dependencies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, variableName, declaredType, origin, constructorArguments, methodInvocations, dependencies);
    }

    @Override
    public String toString() {
        return "DependencyNode{" +
                "type='" + type + '\'' +
                ", variableName='" + variableName + '\'' +
                ", declaredType='" + declaredType + '\'' +
                ", origin=" + origin +
                ", constructorArguments=" + constructorArguments +
                ", methodInvocations=" + methodInvocations +
                ", dependencies=" + dependencies +
                '}';
    }
}
