package com.example.tests.generator.reporting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Scans the project source sets and highlights production classes that have no accompanying tests.
 */
public class ClassWithoutTestsDetector {

    public static final String DEFAULT_MAIN_SOURCE_SET = "src/main/java";
    public static final String DEFAULT_TEST_SOURCE_SET = "src/test/java";

    private final Path projectRoot;
    private final String mainSourceSet;
    private final String testSourceSet;

    public ClassWithoutTestsDetector(Path projectRoot) {
        this(projectRoot, DEFAULT_MAIN_SOURCE_SET, DEFAULT_TEST_SOURCE_SET);
    }

    public ClassWithoutTestsDetector(Path projectRoot, String mainSourceSet, String testSourceSet) {
        this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot");
        this.mainSourceSet = Objects.requireNonNull(mainSourceSet, "mainSourceSet");
        this.testSourceSet = Objects.requireNonNull(testSourceSet, "testSourceSet");
    }

    public List<String> detect() throws IOException {
        Path mainRoot = projectRoot.resolve(mainSourceSet);
        if (!Files.exists(mainRoot)) {
            return List.of();
        }
        Path testRoot = projectRoot.resolve(testSourceSet);
        Set<Path> testFiles = collectTestFiles(testRoot);

        List<String> missingTests = new ArrayList<>();
        try (var stream = Files.walk(mainRoot)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .forEach(path -> {
                        if (!hasTestFor(path, mainRoot, testRoot, testFiles)) {
                            String qualifiedName = toQualifiedName(mainRoot, path);
                            missingTests.add(qualifiedName);
                        }
                    });
        }
        return missingTests;
    }

    private Set<Path> collectTestFiles(Path testRoot) throws IOException {
        if (!Files.exists(testRoot)) {
            return Set.of();
        }
        Set<Path> testFiles = new HashSet<>();
        try (var stream = Files.walk(testRoot)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .forEach(testFiles::add);
        }
        return testFiles;
    }

    private boolean hasTestFor(Path productionFile, Path mainRoot, Path testRoot, Set<Path> testFiles) {
        Path relative = mainRoot.relativize(productionFile);
        String fileName = relative.getFileName().toString();
        String simpleName = fileName.substring(0, fileName.length() - ".java".length());
        List<String> candidates = List.of(simpleName + "Test", simpleName + "Tests", simpleName + "IT", simpleName + "IntegrationTest");

        Path packageDir = relative.getParent();
        for (String candidate : candidates) {
            Path candidateFile = packageDir == null ? testRoot.resolve(candidate + ".java") : testRoot.resolve(packageDir).resolve(candidate + ".java");
            if (Files.exists(candidateFile) || testFiles.contains(candidateFile)) {
                return true;
            }
        }
        return false;
    }

    private String toQualifiedName(Path mainRoot, Path productionFile) {
        Path relative = mainRoot.relativize(productionFile);
        String withoutExtension = relative.toString().replace("\\", "/");
        if (withoutExtension.endsWith(".java")) {
            withoutExtension = withoutExtension.substring(0, withoutExtension.length() - 5);
        }
        return withoutExtension.replace('/', '.');
    }
}
