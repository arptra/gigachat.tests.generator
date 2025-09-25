package com.example.tests.generator.scanner;

import com.example.tests.generator.project.ProjectLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
}
