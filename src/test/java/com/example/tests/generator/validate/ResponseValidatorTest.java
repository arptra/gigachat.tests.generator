package com.example.tests.generator.validate;

import com.example.tests.generator.metadata.ClassMetadata;
import com.example.tests.generator.metadata.MethodMetadata;
import com.example.tests.generator.metadata.ParameterMetadata;
import com.example.tests.generator.metadata.RelatedTypeMetadata;
import com.example.tests.generator.model.ClassKind;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void compilationDoesNotProduceClassFilesInWorkingDirectory() throws Exception {
        ClassMetadata metadata = ClassMetadata.builder()
                .packageName("com.example")
                .className("Subject")
                .build();

        Path classFile = Path.of("NoArtifactsTest.class");
        Files.deleteIfExists(classFile);

        String response = "```java\n"
                + "import org.junit.jupiter.api.Test;\n"
                + "import static org.junit.jupiter.api.Assertions.assertTrue;\n"
                + "\n"
                + "public class NoArtifactsTest {\n"
                + "    @Test\n"
                + "    void keepsWorkingDirectoryClean() {\n"
                + "        assertTrue(true);\n"
                + "    }\n"
                + "}\n"
                + "```";

        ValidationResult result = validator.validate(response, metadata);

        assertTrue(result.isValid());
        assertTrue(Files.notExists(classFile), "Class file should not be emitted to the working directory");
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
        assertTrue(result.getSanitizedCode().isPresent());
    }

    @Test
    void validateFailsWhenImportsAreMissing() {
        String response = "```java\npublic class InvoiceServiceTest {\n    @Test\n    void shouldDoSomething() {}\n}\n```";

        ValidationResult result = validator.validate(response);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("Missing required imports")));
        assertTrue(result.getSanitizedCode().isPresent());
    }

    @Test
    void validateRetainsSanitizedCodeWhenParseFails() {
        String response = "```java\npublic class BrokenTest {\n";

        ValidationResult result = validator.validate(response);

        assertFalse(result.isValid());
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getSanitizedCode().isPresent());
        assertTrue(result.getSanitizedCode().orElseThrow().contains("BrokenTest"));
    }

    @Test
    void validateRejectsInstantiationOfInterfacesWhenMetadataSupplied() {
        ClassMetadata metadata = ClassMetadata.builder()
                .packageName("com.example")
                .className("InvoiceService")
                .kind(ClassKind.INTERFACE)
                .build();

        String response = "```java\n" +
                "package com.example;\n" +
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
        assertTrue(result.getSanitizedCode().isPresent());
    }

    @Test
    void validateAllowsPackagedTestWhenCompilationSucceeds() {
        ClassMetadata metadata = ClassMetadata.builder()
                .packageName("com.example")
                .className("InvoiceService")
                .build();

        String response = "```java\n" +
                "package com.example;\n" +
                "import org.junit.jupiter.api.Test;\n" +
                "import static org.junit.jupiter.api.Assertions.assertNotNull;\n" +
                "\n" +
                "public class InvoiceServiceTest {\n" +
                "    @Test\n" +
                "    void shouldCompile() {\n" +
                "        assertNotNull(\"value\");\n" +
                "    }\n" +
                "}\n" +
                "```";

        ValidationResult result = validator.validate(response, metadata);

        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getSanitizedCode().isPresent());
        assertNotNull(result.getSanitizedCode().orElse(null));
    }

    @Test
    void validateRejectsUnknownMethodUsage() {
        ClassMetadata metadata = ClassMetadata.builder()
                .packageName("com.acme")
                .className("Order")
                .addMethod(MethodMetadata.builder()
                        .name("Order")
                        .returnType("Order")
                        .constructor(true)
                        .addParameter(ParameterMetadata.builder().type("String").name("id").build())
                        .build())
                .addMethod(MethodMetadata.builder()
                        .name("getSubtotal")
                        .returnType("double")
                        .build())
                .build();

        String response = "```java\n" +
                "package com.acme;\n" +
                "import org.junit.jupiter.api.Test;\n" +
                "import org.junit.jupiter.api.Assertions;\n" +
                "\n" +
                "public class OrderTest {\n" +
                "    @Test\n" +
                "    void reportsUnknownMethod() {\n" +
                "        Order order = new Order(\"id\");\n" +
                "        order.getTotal();\n" +
                "    }\n" +
                "}\n" +
                "```";

        ValidationResult result = validator.validate(response, metadata);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("getTotal")));
    }

    @Test
    void validateRejectsUnsupportedConstructorArity() {
        ClassMetadata metadata = ClassMetadata.builder()
                .packageName("com.acme")
                .className("Order")
                .addMethod(MethodMetadata.builder()
                        .name("Order")
                        .returnType("Order")
                        .constructor(true)
                        .addParameter(ParameterMetadata.builder().type("String").name("id").build())
                        .build())
                .build();

        String response = "```java\n" +
                "package com.acme;\n" +
                "import org.junit.jupiter.api.Test;\n" +
                "public class OrderTest {\n" +
                "    @Test\n" +
                "    void rejectsNoArgCtor() {\n" +
                "        new Order();\n" +
                "    }\n" +
                "}\n" +
                "```";

        ValidationResult result = validator.validate(response, metadata);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("constructor")));
    }

    @Test
    void validateRejectsUnknownEnumConstants() {
        RelatedTypeMetadata productCategory = RelatedTypeMetadata.builder()
                .packageName("com.acme")
                .className("ProductCategory")
                .kind(ClassKind.ENUM)
                .addEnumConstant("GROCERY")
                .addEnumConstant("ELECTRONICS")
                .build();

        ClassMetadata metadata = ClassMetadata.builder()
                .packageName("com.acme")
                .className("Order")
                .addSupportingType(productCategory)
                .build();

        String response = "```java\n" +
                "package com.acme;\n" +
                "import org.junit.jupiter.api.Test;\n" +
                "public class OrderTest {\n" +
                "    @Test\n" +
                "    void rejectsUnknownEnumConstant() {\n" +
                "        ProductCategory value = ProductCategory.HOME_GOODS;\n" +
                "    }\n" +
                "}\n" +
                "```";

        ValidationResult result = validator.validate(response, metadata);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("enum constant")));
    }

    @Test
    void validateAcceptsBddMockitoWillReturn() {
        ClassMetadata metadata = ClassMetadata.builder()
                .packageName("com.acme")
                .className("OrderService")
                .build();

        String response = "```java\n"
                + "package com.acme;\n"
                + "import org.junit.jupiter.api.Test;\n"
                + "import org.junit.jupiter.api.extension.ExtendWith;\n"
                + "import org.mockito.Mock;\n"
                + "import org.mockito.junit.jupiter.MockitoExtension;\n"
                + "import static org.mockito.BDDMockito.given;\n"
                + "import static org.mockito.ArgumentMatchers.anyString;\n"
                + "\n"
                + "@ExtendWith(MockitoExtension.class)\n"
                + "public class OrderServiceTest {\n"
                + "\n"
                + "    @Mock\n"
                + "    private Dependency dependency;\n"
                + "\n"
                + "    @Test\n"
                + "    void usesBddMockito() {\n"
                + "        given(dependency.call(anyString())).willReturn(\"value\");\n"
                + "    }\n"
                + "\n"
                + "    private interface Dependency {\n"
                + "        String call(String input);\n"
                + "    }\n"
                + "}\n"
                + "```";

        ValidationResult result = validator.validate(response, metadata);

        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void validateRejectsManualMockitoAnnotationsInitialisation() {
        ClassMetadata metadata = ClassMetadata.builder()
                .packageName("com.acme")
                .className("Order")
                .build();

        String response = "```java\n"
                + "package com.acme;\n"
                + "import org.junit.jupiter.api.Test;\n"
                + "import org.mockito.MockitoAnnotations;\n"
                + "\n"
                + "public class OrderTest {\n"
                + "    @Test\n"
                + "    void avoidsManualMockitoSetup() {\n"
                + "        MockitoAnnotations.openMocks(this);\n"
                + "    }\n"
                + "}\n"
                + "```";

        ValidationResult result = validator.validate(response, metadata);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(error -> error.contains("MockitoExtension")));
    }

    @Test
    void validateRejectsRecordComponentFieldAccess() {
        RelatedTypeMetadata discountOutcome = RelatedTypeMetadata.builder()
                .packageName("com.acme")
                .className("DiscountOutcome")
                .kind(ClassKind.RECORD)
                .addMethod(MethodMetadata.builder()
                        .name("DiscountOutcome")
                        .returnType("DiscountOutcome")
                        .constructor(true)
                        .addParameter(ParameterMetadata.builder().type("boolean").name("applied").build())
                        .addParameter(ParameterMetadata.builder().type("double").name("discountAmount").build())
                        .addParameter(ParameterMetadata.builder().type("String").name("reason").build())
                        .build())
                .addMethod(MethodMetadata.builder()
                        .name("applied")
                        .returnType("boolean")
                        .build())
                .addMethod(MethodMetadata.builder()
                        .name("discountAmount")
                        .returnType("double")
                        .build())
                .addMethod(MethodMetadata.builder()
                        .name("reason")
                        .returnType("String")
                        .build())
                .addMethod(MethodMetadata.builder()
                        .name("applied")
                        .returnType("DiscountOutcome")
                        .staticMethod(true)
                        .addParameter(ParameterMetadata.builder().type("double").name("amount").build())
                        .addParameter(ParameterMetadata.builder().type("String").name("reason").build())
                        .build())
                .build();

        ClassMetadata metadata = ClassMetadata.builder()
                .packageName("com.acme")
                .className("DiscountRule")
                .addSupportingType(discountOutcome)
                .build();

        String response = "```java\n" +
                "package com.acme;\n" +
                "import org.junit.jupiter.api.Test;\n" +
                "public class DiscountOutcomeTest {\n" +
                "    @Test\n" +
                "    void rejectsFieldAccess() {\n" +
                "        DiscountOutcome outcome = DiscountOutcome.applied(10.0, \"reason\");\n" +
                "        boolean value = outcome.applied;\n" +
                "    }\n" +
                "}\n" +
                "```";

        ValidationResult result = validator.validate(response, metadata);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("Direct field access")));
    }

    @Test
    void validateRejectsMissingImportsDiscoveredDuringCompilation() {
        ClassMetadata metadata = ClassMetadata.builder()
                .packageName("com.example")
                .className("SampleService")
                .build();

        String response = "```java\n" +
                "package com.example;\n" +
                "import org.junit.jupiter.api.Test;\n" +
                "\n" +
                "public class SampleServiceTest {\n" +
                "    @Test\n" +
                "    void detectsMissingImport() {\n" +
                "        Map<String, String> payload = Map.of();\n" +
                "    }\n" +
                "}\n" +
                "```";

        ValidationResult result = validator.validate(response, metadata);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("Map")));
    }
}
