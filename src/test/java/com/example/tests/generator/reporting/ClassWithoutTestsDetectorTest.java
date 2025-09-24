package com.example.tests.generator.reporting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ClassWithoutTestsDetectorTest {

    @TempDir
    Path tempDir;

    @Test
    void detectsMissingTests() throws IOException {
        Path mainDir = tempDir.resolve("src/main/java/com/example/app");
        Files.createDirectories(mainDir);
        Path service = mainDir.resolve("Service.java");
        Files.writeString(service, "package com.example.app; class Service {}");
        Path controller = mainDir.resolve("Controller.java");
        Files.writeString(controller, "package com.example.app; class Controller {}");

        Path testDir = tempDir.resolve("src/test/java/com/example/app");
        Files.createDirectories(testDir);
        Path serviceTest = testDir.resolve("ServiceTest.java");
        Files.writeString(serviceTest, "package com.example.app; class ServiceTest {}");

        ClassWithoutTestsDetector detector = new ClassWithoutTestsDetector(tempDir);
        List<String> missing = detector.detect();

        assertTrue(missing.contains("com.example.app.Controller"));
        assertFalse(missing.contains("com.example.app.Service"));
    }
}
