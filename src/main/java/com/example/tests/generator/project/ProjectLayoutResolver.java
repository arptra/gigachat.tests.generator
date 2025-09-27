package com.example.tests.generator.project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Detects the primary source and test directories of a project.
 */
public final class ProjectLayoutResolver {

    public static final String DEFAULT_MAIN_SOURCE_SET = "src/main/java";
    public static final String DEFAULT_TEST_SOURCE_SET = "src/test/java";

    private static final List<String> MAIN_SOURCE_CANDIDATES = List.of(
            "src/main/java",
            "src/main",
            "src"
    );

    private static final List<String> TEST_SOURCE_CANDIDATES = List.of(
            "src/test/java",
            "src/test",
            "test",
            "tests"
    );

    private ProjectLayoutResolver() {
    }

    public static ProjectLayout detect(Path projectRoot) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Path normalisedRoot = projectRoot.toAbsolutePath().normalize();
        String main = findExisting(normalisedRoot, MAIN_SOURCE_CANDIDATES, DEFAULT_MAIN_SOURCE_SET);
        String test = findExisting(normalisedRoot, TEST_SOURCE_CANDIDATES, DEFAULT_TEST_SOURCE_SET);
        return new ProjectLayout(main, test);
    }

    private static String findExisting(Path root, List<String> candidates, String fallback) {
        for (String candidate : candidates) {
            if (exists(root.resolve(candidate))) {
                return normalise(candidate);
            }
        }
        return normalise(fallback);
    }

    private static boolean exists(Path path) {
        return Files.exists(path) && Files.isDirectory(path);
    }

    private static String normalise(String path) {
        return path.replace('\\', '/');
    }
}
