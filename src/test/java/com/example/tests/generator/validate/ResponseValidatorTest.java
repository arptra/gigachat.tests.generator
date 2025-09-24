package com.example.tests.generator.validate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseValidatorTest {

    private final ResponseValidator validator = new ResponseValidator();

    @Test
    void validateReturnsSuccessForWellFormedResponse() {
        String response = "Here is the generated test:\n" +
                "```java\n" +
                "import org.junit.jupiter.api.Test;\n" +
                "import org.mockito.Mockito;\n" +
                "\n" +
                "public class InvoiceServiceTest {\n" +
                "    @Test\n" +
                "    void shouldDoSomething() {\n" +
                "        Mockito.mock(Object.class);\n" +
                "    }\n" +
                "}\n" +
                "```";

        ValidationResult result = validator.validate(response);

        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getSanitizedCode().isPresent());
    }

    @Test
    void validateFailsWhenMissingCodeBlock() {
        ValidationResult result = validator.validate("No code here");

        assertFalse(result.isValid());
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getSanitizedCode().isEmpty());
    }

    @Test
    void validateFailsWhenMissingTestAnnotation() {
        String response = "```java\nimport org.junit.jupiter.api.Test;\nimport org.mockito.Mockito;\npublic class InvoiceServiceTest {\n    void shouldDoSomething() {}\n}\n```";

        ValidationResult result = validator.validate(response);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("@Test")));
    }

    @Test
    void validateFailsWhenImportsAreMissing() {
        String response = "```java\npublic class InvoiceServiceTest {\n    @Test\n    void shouldDoSomething() {}\n}\n```";

        ValidationResult result = validator.validate(response);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("Missing required imports")));
    }
}
