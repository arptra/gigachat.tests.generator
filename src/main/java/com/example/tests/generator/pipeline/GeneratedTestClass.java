package com.example.tests.generator.pipeline;

import java.util.Objects;

/**
 * Representation of a generated test class before it is persisted.
 */
public class GeneratedTestClass {

    private final String packageName;
    private final String className;
    private final String sourceCode;

    public GeneratedTestClass(String packageName, String className, String sourceCode) {
        this.packageName = packageName == null ? "" : packageName.trim();
        this.className = Objects.requireNonNull(className, "className");
        this.sourceCode = Objects.requireNonNull(sourceCode, "sourceCode");
    }

    public String getPackageName() {
        return packageName;
    }

    public String getClassName() {
        return className;
    }

    public String getSourceCode() {
        return sourceCode;
    }
}
