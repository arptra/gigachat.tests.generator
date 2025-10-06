package com.example.tests.generator.validate;

import com.example.tests.generator.metadata.ClassMetadata;
import com.example.tests.generator.model.ClassKind;
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
    void validateSucceedsWhenJavaProvidedWithoutCodeFence() {
        String response = "Some intro text\n" +
                "import org.junit.jupiter.api.Test;\n" +
                "import static org.junit.jupiter.api.Assertions.assertTrue;\n" +
                "\n" +
                "public class InvoiceServiceTest {\n" +
                "    @Test\n" +
                "    void shouldDoSomething() {\n" +
                "        assertTrue(true);\n" +
                "    }\n" +
                "}\n" +
                "Thanks!";

        ValidationResult result = validator.validate(response);

        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getSanitizedCode().isPresent());
        assertTrue(result.getSanitizedCode().orElseThrow().startsWith("import"));
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

    @Test
    void validateRejectsInstantiationOfInterfacesWhenMetadataSupplied() {
        ClassMetadata metadata = ClassMetadata.builder()
                .packageName("com.example")
                .className("InvoiceService")
                .kind(ClassKind.INTERFACE)
                .build();

        String response = "```java\n" +
                "import org.junit.jupiter.api.Test;\n" +
                "import org.junit.jupiter.api.Assertions;\n" +
                "\n" +
                "public class InvoiceServiceTest {\n" +
                "    @Test\n" +
                "    void shouldNotInstantiateInterface() {\n" +
                "        new InvoiceService();\n" +
                "    }\n" +
                "}\n" +
                "```";

        ValidationResult result = validator.validate(response, metadata);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("InvoiceService")));
    }
}
