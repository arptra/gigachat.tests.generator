package com.example.tests.generator.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Describes a class method or constructor with its signature and annotations.
 */
public class MethodMetadata {

    private final String name;
    private final String signature;
    private final boolean constructor;
    private final List<String> annotations;

    public MethodMetadata(String name, String signature, boolean constructor, List<String> annotations) {
        this.name = Objects.requireNonNull(name, "name");
        this.signature = Objects.requireNonNull(signature, "signature");
        this.constructor = constructor;
        this.annotations = Collections.unmodifiableList(new ArrayList<>(annotations == null ? List.of() : annotations));
    }

    public String getName() {
        return name;
    }

    public String getSignature() {
        return signature;
    }

    public boolean isConstructor() {
        return constructor;
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    @Override
    public String toString() {
        return "MethodMetadata{" +
                "name='" + name + '\'' +
                ", constructor=" + constructor +
                '}';
    }
}
