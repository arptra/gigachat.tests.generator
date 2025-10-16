package com.example.tests.generator.scanner;

import com.example.tests.generator.model.ClassMetadata;
import com.example.tests.generator.project.ProjectLayout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void scansClassesInsideTestPackages() throws IOException {
        Path abonentDir = tempDir.resolve("src/main/java/mtd/abonent");
        Files.createDirectories(abonentDir);
        Files.writeString(abonentDir.resolve("DELETE_AUTO.java"),
                "package mtd.abonent; class DELETE_AUTO extends rt.Package {}");

        Path legacyDir = tempDir.resolve("src/main/java/com/example/test");
        Files.createDirectories(legacyDir);
        Files.writeString(legacyDir.resolve("LegacyService.java"),
                "package com.example.test; class LegacyService {}");

        Path legacyTests = tempDir.resolve("src/test");
        Files.createDirectories(legacyTests);
        Files.writeString(legacyTests.resolve("LegacyServiceTest.java"),
                "package com.example.test; class LegacyServiceTest {}");

        ProjectLayout layout = new ProjectLayout("src/main/java", "src/test");
        ProjectScanner scanner = new ProjectScanner(tempDir, layout);

        List<com.example.tests.generator.model.ClassMetadata> classes = scanner.scan();

        assertTrue(classes.stream()
                .anyMatch(metadata -> "mtd.abonent.DELETE_AUTO".equals(metadata.getQualifiedName())));
        assertTrue(classes.stream()
                .anyMatch(metadata -> "com.example.test.LegacyService".equals(metadata.getQualifiedName())));
        assertFalse(classes.stream()
                .anyMatch(metadata -> "com.example.test.LegacyServiceTest".equals(metadata.getQualifiedName())));
    }

    @Test
    void capturesEnumConstants() throws IOException {
        Path enumDir = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(enumDir);
        Files.writeString(enumDir.resolve("Sample.java"), String.join(System.lineSeparator(),
                "package demo;",
                "",
                "public enum Sample {",
                "    FIRST,",
                "    SECOND",
                "}",
                ""));

        ProjectLayout layout = new ProjectLayout("src/main/java", "src/test/java");
        ProjectScanner scanner = new ProjectScanner(tempDir, layout);

        List<ClassMetadata> metadata = scanner.scan();
        ClassMetadata sample = metadata.stream()
                .filter(candidate -> candidate.getQualifiedName().equals("demo.Sample"))
                .findFirst()
                .orElseThrow();

        assertTrue(sample.isEnumType());
        assertEquals(List.of("FIRST", "SECOND"), sample.getEnumConstants());
    }

    @Test
    void discoversNestedTypesAndRecords() throws IOException {
        Path sourceDir = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("Outer.java"), String.join(System.lineSeparator(),
                "package demo;",
                "",
                "public class Outer {",
                "    public record Inner(String value) { }",
                "}",
                ""));

        ProjectLayout layout = new ProjectLayout("src/main/java", "src/test/java");
        ProjectScanner scanner = new ProjectScanner(tempDir, layout);

        List<ClassMetadata> metadata = scanner.scan();

        assertTrue(metadata.stream()
                .anyMatch(candidate -> candidate.getQualifiedName().equals("demo.Outer")));

        ClassMetadata inner = metadata.stream()
                .filter(candidate -> candidate.getQualifiedName().equals("demo.Outer.Inner"))
                .findFirst()
                .orElseThrow();

        assertTrue(inner.isRecord());
        assertTrue(inner.getMethods().stream()
                .anyMatch(method -> method.getName().equals("Inner")));
        assertTrue(inner.getMethods().stream()
                .anyMatch(method -> method.getName().equals("value")));
    }
}
