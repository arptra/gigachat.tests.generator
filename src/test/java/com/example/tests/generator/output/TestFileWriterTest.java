package com.example.tests.generator.output;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestFileWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesFileToPackageStructure() throws IOException {
        TestFileWriter writer = new TestFileWriter(tempDir);
        String code = "import org.junit.jupiter.api.Test;" + System.lineSeparator() +
                "class SampleTest {" + System.lineSeparator() +
                "    @Test void ok() {}" + System.lineSeparator() +
                "}";

        Path path = writer.writeTestFile("com.example.demo", "SampleTest", code);

        Path expected = tempDir.resolve("src/test/java/com/example/demo/SampleTest.java");
        assertEquals(expected, path);
        assertTrue(Files.exists(expected));
        String content = Files.readString(expected);
        assertTrue(content.contains("package com.example.demo;"));
        assertTrue(content.endsWith(System.lineSeparator()));
    }

    @Test
    void writesToDefaultPackageWhenPackageMissing() throws IOException {
        TestFileWriter writer = new TestFileWriter(tempDir);
        String code = "class PlainTest {}";

        Path path = writer.writeTestFile(null, "PlainTest", code);

        Path expected = tempDir.resolve("src/test/java/PlainTest.java");
        assertEquals(expected, path);
        assertTrue(Files.exists(expected));
        String content = Files.readString(expected);
        assertFalse(content.contains("package"));
    }

    @Test
    void supportsLegacyTestSourceSet() throws IOException {
        TestFileWriter writer = new TestFileWriter(tempDir, "src/test");
        String code = "package mtd.abonent; class DeleteAutoTest {}";

        Path path = writer.writeTestFile("mtd.abonent", "DeleteAutoTest", code);

        Path expected = tempDir.resolve("src/test/mtd/abonent/DeleteAutoTest.java");
        assertEquals(expected, path);
        assertTrue(Files.exists(expected));
        String content = Files.readString(expected);
        assertTrue(content.contains("package mtd.abonent;"));
    }
}
