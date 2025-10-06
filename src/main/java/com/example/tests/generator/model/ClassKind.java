package com.example.tests.generator.model;

/**
 * Represents the high level kind of a Java type discovered during scanning.
 */
public enum ClassKind {
    CLASS,
    INTERFACE,
    ENUM,
    RECORD;

    public boolean isEnum() {
        return this == ENUM;
    }

    public boolean isInterface() {
        return this == INTERFACE;
    }

    public boolean isRecord() {
        return this == RECORD;
    }
}
