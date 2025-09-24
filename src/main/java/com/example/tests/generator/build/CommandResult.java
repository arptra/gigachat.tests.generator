package com.example.tests.generator.build;

/**
 * Result of a command execution.
 */
public record CommandResult(int exitCode, String output) {

    public boolean isSuccessful() {
        return exitCode == 0;
    }
}
