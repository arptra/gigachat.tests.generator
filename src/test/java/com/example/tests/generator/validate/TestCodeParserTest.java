package com.example.tests.generator.validate;

import com.example.tests.generator.pipeline.GeneratedTestClass;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCodeParserTest {

    private final TestCodeParser parser = new TestCodeParser();

    @Test
    void parsesValidCode() {
        String code = "package com.example;\n\n" +
                "import org.junit.jupiter.api.Test;\n\n" +
                "public class SampleServiceTest {\n" +
                "    @Test void works() {}\n" +
                "}";

        Optional<GeneratedTestClass> result = parser.parse(code);

        assertTrue(result.isPresent());
        GeneratedTestClass testClass = result.orElseThrow();
        assertEquals("com.example", testClass.getPackageName());
        assertEquals("SampleServiceTest", testClass.getClassName());
        assertEquals(code, testClass.getSourceCode());
    }

    @Test
    void returnsEmptyWhenClassMissing() {
        String code = "package com.example;\n\npublic interface Contract {}";

        Optional<GeneratedTestClass> result = parser.parse(code);

        assertFalse(result.isPresent());
    }

    @Test
    void stillParsesNonPublicTestClass() {
        String code = "package com.example;\n\n" +
                "import org.junit.jupiter.api.Test;\n\n" +
                "class SampleServiceTest {\n" +
                "    @Test void works() {}\n" +
                "}";

        Optional<GeneratedTestClass> result = parser.parse(code);

        assertTrue(result.isPresent());
        GeneratedTestClass testClass = result.orElseThrow();
        assertEquals("com.example", testClass.getPackageName());
        assertEquals("SampleServiceTest", testClass.getClassName());
    }
}
