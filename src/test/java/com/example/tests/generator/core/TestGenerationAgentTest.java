package com.example.tests.generator.core;

import com.example.tests.generator.metadata.ClassMetadata;
import com.example.tests.generator.prompt.PromptBuilder;
import com.example.tests.generator.validate.ResponseValidator;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestGenerationAgentTest {

    private final PromptBuilder promptBuilder = new PromptBuilder();
    private final ResponseValidator responseValidator = new ResponseValidator();

    @Test
    void retriesUntilValidResponse() {
        ClassMetadata metadata = ClassMetadata.builder()
                .packageName("com.example")
                .className("InvoiceService")
                .description("Calculates invoices")
                .build();

        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<String> secondPrompt = new AtomicReference<>();

        TestResponseProvider provider = prompt -> {
            int attempt = attempts.getAndIncrement();
            if (attempt == 0) {
                return "```java\npublic class InvoiceServiceTest {}\n```";
            }
            secondPrompt.set(prompt);
            return "```java\n" +
                    "package com.example;\n\n" +
                    "import org.junit.jupiter.api.Test;\n" +
                    "import org.junit.jupiter.api.extension.ExtendWith;\n" +
                    "import org.mockito.junit.jupiter.MockitoExtension;\n\n" +
                    "import static org.junit.jupiter.api.Assertions.assertNotNull;\n" +
                    "import static org.mockito.Mockito.mock;\n\n" +
                    "@ExtendWith(MockitoExtension.class)\n" +
                    "public class InvoiceServiceTest {\n" +
                    "    @Test void shouldWork() { assertNotNull(mock(Object.class)); }\n" +
                    "}\n```";
        };

        TestGenerationAgent agent = new TestGenerationAgent(promptBuilder, responseValidator, provider, 2);

        String result = agent.generateTests(metadata);

        assertEquals(2, attempts.get());
        assertTrue(result.contains("mock(Object.class)"));
        assertTrue(secondPrompt.get().contains("Missing required imports"));
    }

    @Test
    void throwsWhenRetriesExhausted() {
        ClassMetadata metadata = ClassMetadata.builder()
                .packageName("com.example")
                .className("InvoiceService")
                .description("Calculates invoices")
                .build();

        TestResponseProvider provider = prompt -> "```java\npublic class InvoiceServiceTest {}\n```";

        TestGenerationAgent agent = new TestGenerationAgent(promptBuilder, responseValidator, provider, 1);

        assertThrows(IllegalStateException.class, () -> agent.generateTests(metadata));
    }
}
