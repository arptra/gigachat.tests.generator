package com.example.tests.generator.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectLayoutResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void detectsLegacyTestLayout() throws IOException {
        Files.createDirectories(tempDir.resolve("src/main/java"));
        Files.createDirectories(tempDir.resolve("src/test"));

        ProjectLayout layout = ProjectLayoutResolver.detect(tempDir);

        assertEquals("src/main/java", layout.mainSourceSet());
        assertEquals("src/test", layout.testSourceSet());
    }

    @Test
    void fallsBackToDefaultsWhenDirectoriesMissing() {
        ProjectLayout layout = ProjectLayoutResolver.detect(tempDir);

        assertEquals(ProjectLayoutResolver.DEFAULT_MAIN_SOURCE_SET, layout.mainSourceSet());
        assertEquals(ProjectLayoutResolver.DEFAULT_TEST_SOURCE_SET, layout.testSourceSet());
    }
}
