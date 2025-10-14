package com.example.tests.orchestration.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Describes the object construction graph and method invocations observed inside
 * a particular method.
 */
public final class MethodDependencyGraph {

    private final String fullyQualifiedClassName;
    private final String methodName;
    private final List<MethodParameter> parameters;
    private final List<DependencyNode> dependencies;
    private final List<MethodInvocation> unattachedInvocations;

    public MethodDependencyGraph(String fullyQualifiedClassName,
                                 String methodName,
                                 List<MethodParameter> parameters,
                                 List<DependencyNode> dependencies,
                                 List<MethodInvocation> unattachedInvocations) {
        this.fullyQualifiedClassName = Objects.requireNonNull(fullyQualifiedClassName, "fullyQualifiedClassName");
        this.methodName = Objects.requireNonNull(methodName, "methodName");
        this.parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
        this.dependencies = Collections.unmodifiableList(new ArrayList<>(dependencies));
        this.unattachedInvocations = Collections.unmodifiableList(new ArrayList<>(unattachedInvocations));
    }

    public String getFullyQualifiedClassName() {
        return fullyQualifiedClassName;
    }

    public String getMethodName() {
        return methodName;
    }

    public List<MethodParameter> getParameters() {
        return parameters;
    }

    public List<DependencyNode> getDependencies() {
        return dependencies;
    }

    public List<MethodInvocation> getUnattachedInvocations() {
        return unattachedInvocations;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MethodDependencyGraph that)) {
            return false;
        }
        return Objects.equals(fullyQualifiedClassName, that.fullyQualifiedClassName)
                && Objects.equals(methodName, that.methodName)
                && Objects.equals(parameters, that.parameters)
                && Objects.equals(dependencies, that.dependencies)
                && Objects.equals(unattachedInvocations, that.unattachedInvocations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullyQualifiedClassName, methodName, parameters, dependencies, unattachedInvocations);
    }

    @Override
    public String toString() {
        return "MethodDependencyGraph{" +
                "class='" + fullyQualifiedClassName + '\'' +
                ", method='" + methodName + '\'' +
                ", parameters=" + parameters +
                ", dependencies=" + dependencies +
                ", unattachedInvocations=" + unattachedInvocations +
                '}';
    }
}
