package com.example.tests.generator.output;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Persists generated test sources to the conventional test source set while respecting the
 * package hierarchy declared for the class.
 */
public class TestFileWriter {

    public static final String DEFAULT_TEST_SOURCE_SET = "src/test/java";

    private final Path projectRoot;
    private final String testSourceSet;

    public TestFileWriter(Path projectRoot) {
        this(projectRoot, DEFAULT_TEST_SOURCE_SET);
    }

    public TestFileWriter(Path projectRoot, String testSourceSet) {
        this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot");
        this.testSourceSet = Objects.requireNonNull(testSourceSet, "testSourceSet");
    }

    /**
     * Writes the supplied {@code sourceCode} to the correct location inside the test source set.
     * The directory structure follows the provided package name.
     *
     * @param packageName package of the generated test (may be {@code null} or blank for the default package})
     * @param className name of the generated test class
     * @param sourceCode compiled source code of the generated test
     * @return path to the written file
     * @throws IOException if the file cannot be created
     */
    public Path writeTestFile(String packageName, String className, String sourceCode) throws IOException {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(sourceCode, "sourceCode");

        Path targetDirectory = resolvePackageDirectory(packageName);
        Files.createDirectories(targetDirectory);

        Path targetFile = targetDirectory.resolve(className + ".java");
        String normalizedSource = normalizeSource(packageName, sourceCode);
        Files.writeString(targetFile, normalizedSource, StandardCharsets.UTF_8);
        return targetFile;
    }

    private Path resolvePackageDirectory(String packageName) {
        Path testRoot = projectRoot.resolve(testSourceSet);
        if (packageName == null || packageName.isBlank()) {
            return testRoot;
        }
        String packagePath = packageName.replace('.', '/');
        return testRoot.resolve(packagePath);
    }

    private String normalizeSource(String packageName, String sourceCode) {
        String content = sourceCode;
        if (packageName != null && !packageName.isBlank()) {
            String trimmed = sourceCode.stripLeading();
            String packageDeclaration = "package " + packageName + ";";
            if (trimmed.startsWith("package ")) {
                if (!trimmed.startsWith(packageDeclaration)) {
                    int semicolonIndex = trimmed.indexOf(';');
                    String remainder = semicolonIndex >= 0 ? trimmed.substring(semicolonIndex + 1).stripLeading() : trimmed;
                    content = packageDeclaration + System.lineSeparator() + System.lineSeparator() + remainder;
                }
            } else {
                content = packageDeclaration + System.lineSeparator() + System.lineSeparator() + trimmed;
            }
        }
        if (!content.endsWith(System.lineSeparator())) {
            content = content + System.lineSeparator();
        }
        return content;
    }
}
