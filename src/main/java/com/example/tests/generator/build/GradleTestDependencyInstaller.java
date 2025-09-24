package com.example.tests.generator.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Ensures that the target Gradle project has the minimal set of dependencies
 * required for the generated tests to compile. Currently it focuses on the
 * Groovy DSL build files (build.gradle).
 */
public class GradleTestDependencyInstaller {

    private static final Logger LOGGER = Logger.getLogger(GradleTestDependencyInstaller.class.getName());
    private static final String BUILD_GRADLE = "build.gradle";

    private final Path projectRoot;

    private static final List<Dependency> REQUIRED_DEPENDENCIES = List.of(
            new Dependency("testImplementation", "org.junit.jupiter:junit-jupiter:5.10.2"),
            new Dependency("testRuntimeOnly", "org.junit.jupiter:junit-jupiter-engine:5.10.2"),
            new Dependency("testImplementation", "org.mockito:mockito-core:5.12.0"),
            new Dependency("testImplementation", "org.mockito:mockito-junit-jupiter:5.12.0")
    );

    public GradleTestDependencyInstaller(Path projectRoot) {
        this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot");
    }

    /**
     * Ensures that the Gradle build file contains all required test dependencies.
     * Missing dependencies are appended to the dependencies block. If the block
     * is absent, it will be created at the end of the file.
     */
    public void ensureTestDependencies() {
        Path buildFile = projectRoot.resolve(BUILD_GRADLE);
        if (!Files.exists(buildFile)) {
            LOGGER.warning(() -> "build.gradle not found at " + buildFile + ". Skipping dependency installation.");
            return;
        }

        String original;
        try {
            original = Files.readString(buildFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warning(() -> "Failed to read build.gradle: " + e.getMessage());
            return;
        }

        ModificationResult modificationResult = ensureDependenciesBlock(original);
        if (!modificationResult.modified()) {
            LOGGER.info("All required test dependencies already present in build.gradle");
            return;
        }

        try {
            Files.writeString(buildFile, modificationResult.updatedContent(), StandardCharsets.UTF_8);
            LOGGER.info("Added missing test dependencies to build.gradle");
        } catch (IOException e) {
            LOGGER.warning(() -> "Failed to write build.gradle: " + e.getMessage());
        }
    }

    private ModificationResult ensureDependenciesBlock(String content) {
        Optional<BlockRange> blockRange = findDependenciesBlock(content);
        if (blockRange.isEmpty()) {
            StringBuilder builder = new StringBuilder(content);
            if (!content.endsWith(System.lineSeparator())) {
                builder.append(System.lineSeparator());
            }
            builder.append(System.lineSeparator())
                    .append("dependencies {").append(System.lineSeparator());
            for (Dependency dependency : REQUIRED_DEPENDENCIES) {
                builder.append("    ")
                        .append(dependency.configuration()).append(' ')
                        .append('\'').append(dependency.notation()).append('\'')
                        .append(System.lineSeparator());
            }
            builder.append('}').append(System.lineSeparator());
            return new ModificationResult(builder.toString(), true);
        }

        BlockRange range = blockRange.get();
        String inside = content.substring(range.openBraceIndex() + 1, range.closeBraceIndex());
        List<Dependency> missing = findMissingDependencies(inside);
        if (missing.isEmpty()) {
            return new ModificationResult(content, false);
        }

        StringBuilder blockBuilder = new StringBuilder(inside);
        if (!inside.endsWith(System.lineSeparator())) {
            blockBuilder.append(System.lineSeparator());
        }
        for (Dependency dependency : missing) {
            blockBuilder.append("    ")
                    .append(dependency.configuration()).append(' ')
                    .append('\'').append(dependency.notation()).append('\'')
                    .append(System.lineSeparator());
        }

        String updated = content.substring(0, range.openBraceIndex() + 1)
                + blockBuilder
                + content.substring(range.closeBraceIndex());
        return new ModificationResult(updated, true);
    }

    private Optional<BlockRange> findDependenciesBlock(String content) {
        int keywordIndex = content.indexOf("dependencies");
        if (keywordIndex < 0) {
            return Optional.empty();
        }
        int braceStart = content.indexOf('{', keywordIndex);
        if (braceStart < 0) {
            return Optional.empty();
        }
        int braceEnd = findMatchingBrace(content, braceStart);
        if (braceEnd < 0) {
            return Optional.empty();
        }
        return Optional.of(new BlockRange(braceStart, braceEnd));
    }

    private int findMatchingBrace(String content, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private List<Dependency> findMissingDependencies(String blockContent) {
        List<Dependency> missing = new ArrayList<>();
        for (Dependency dependency : REQUIRED_DEPENDENCIES) {
            if (!blockContent.contains(dependency.notation())) {
                missing.add(dependency);
            }
        }
        return missing;
    }

    private record Dependency(String configuration, String notation) {}

    private record BlockRange(int openBraceIndex, int closeBraceIndex) {}

    private record ModificationResult(String updatedContent, boolean modified) {}
}
