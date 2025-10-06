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

    @Test
    void restoresOriginalContentWhenBackupExists() throws IOException {
        TestFileWriter writer = new TestFileWriter(tempDir);
        String original = "package com.example;" + System.lineSeparator() + "class SampleTest {}" + System.lineSeparator();
        Path existing = writer.writeTestFile("com.example", "SampleTest", original);
        String modified = "package com.example;" + System.lineSeparator() + "class SampleTest { }" + System.lineSeparator();

        TestFileWriter.WrittenTestFile written = writer.writeTestFileWithBackup("com.example", "SampleTest", modified);
        writer.restorePreviousContent(written);

        String restored = Files.readString(existing);
        assertEquals(original, restored);
    }

    @Test
    void restoreDeletesNewFileWhenNoBackup() throws IOException {
        TestFileWriter writer = new TestFileWriter(tempDir);
        TestFileWriter.WrittenTestFile written = writer.writeTestFileWithBackup("", "TemporaryTest", "class TemporaryTest {}");

        writer.restorePreviousContent(written);

        Path expected = tempDir.resolve("src/test/java/TemporaryTest.java");
        assertFalse(Files.exists(expected));
    }
}
