package com.example.tests.generator.cli;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CliArgumentsTest {

    @Test
    void parsesAllFlags() {
        CliArguments arguments = CliArguments.parse(new String[]{
                "--project", "./demo",
                "--class", "com.example.Service",
                "--class", "com.example.Repository",
                "--max-retries", "3",
                "--limit", "5"
        });

        assertEquals(Paths.get("./demo").toAbsolutePath().normalize(), arguments.projectRoot());
        assertIterableEquals(java.util.List.of("com.example.Service", "com.example.Repository"), arguments.targetClasses());
        assertEquals(3, arguments.maxRetries());
        assertEquals(5, arguments.limit());
    }

    @Test
    void throwsOnMissingValue() {
        assertThrows(IllegalArgumentException.class, () -> CliArguments.parse(new String[]{"--project"}));
    }
}
