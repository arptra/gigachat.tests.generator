package com.example.tests.generator.core;

/**
 * Represents an external agent capable of generating test code from a prompt.
 */
@FunctionalInterface
public interface TestResponseProvider {

    String generate(String prompt);
}
