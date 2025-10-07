package com.example.tests.generator.prompt;

import com.example.agent.providers.LLMClient;
import com.example.tests.generator.metadata.ClassMetadata;
import com.example.tests.generator.metadata.RelatedTypeMetadata;
import com.example.tests.generator.model.ClassKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptRefinementAgentTest {

    @Test
    void generatesPrefaceWhenBlockingErrorsRepeat() {
        RecordingLLMClient client = new RecordingLLMClient("Ask for the enum values before coding.");
        PromptRefinementAgent agent = new PromptRefinementAgent(client);

        RelatedTypeMetadata enumMetadata = RelatedTypeMetadata.builder()
                .packageName("com.acme.discount")
                .className("ProductCategory")
                .qualifiedName("com.acme.discount.ProductCategory")
                .kind(ClassKind.ENUM)
                .addEnumConstant("GROCERY")
                .addEnumConstant("ELECTRONICS")
                .build();

        ClassMetadata metadata = ClassMetadata.builder()
                .packageName("com.acme.discount")
                .className("Order")
                .addSupportingType(enumMetadata)
                .build();

        List<String> feedback = List.of(
                "ProductCategory does not declare enum constant FOOD."
        );

        Optional<String> preface = agent.generatePreface(metadata, feedback);

        assertTrue(preface.isPresent());
        assertEquals("Ask for the enum values before coding.", preface.get());
        assertTrue(client.lastPrompt().contains("ProductCategory"));
        assertTrue(client.lastPrompt().contains("GROCERY"));
    }

    @Test
    void skipsHelperWhenFeedbackIsNonBlocking() {
        RecordingLLMClient client = new RecordingLLMClient("irrelevant");
        PromptRefinementAgent agent = new PromptRefinementAgent(client);

        ClassMetadata metadata = ClassMetadata.builder()
                .packageName("com.acme.discount")
                .className("Order")
                .build();

        List<String> feedback = List.of("Spacing could be improved.");

        Optional<String> preface = agent.generatePreface(metadata, feedback);

        assertTrue(preface.isEmpty());
        assertEquals(null, client.lastPrompt());
    }

    private static final class RecordingLLMClient implements LLMClient {

        private final String response;
        private final AtomicReference<String> lastPrompt = new AtomicReference<>();

        private RecordingLLMClient(String response) {
            this.response = response;
        }

        @Override
        public String sendPrompt(String prompt, Map<String, Object> options) {
            lastPrompt.set(prompt);
            return response;
        }

        @Override
        public Stream<String> streamResponses(String prompt, Map<String, Object> options) {
            throw new UnsupportedOperationException("Streaming not supported in tests");
        }

        @Override
        public void handleRateLimits(int statusCode, String responseBody) {
            // no-op for tests
        }

        String lastPrompt() {
            return lastPrompt.get();
        }
    }
}

