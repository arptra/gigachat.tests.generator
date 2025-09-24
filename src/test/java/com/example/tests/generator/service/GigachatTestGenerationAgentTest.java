package com.example.tests.generator.service;

import com.example.agent.providers.LLMClient;
import com.example.tests.generator.model.ClassMetadata;
import com.example.tests.generator.model.MethodMetadata;
import com.example.tests.generator.model.TestGenerationResult;
import com.example.tests.generator.prompts.PromptFactory;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GigachatTestGenerationAgentTest {

    @Test
    void orchestratesMultiplePrompts() {
        RecordingClient client = new RecordingClient();
        GigachatTestGenerationAgent agent = new GigachatTestGenerationAgent(client, new PromptFactory());

        ClassMetadata metadata = ClassMetadata.builder()
                .packageName("com.example.demo")
                .className("Calculator")
                .description("Performs arithmetic operations")
                .addMethod(MethodMetadata.builder()
                        .name("sum")
                        .returnType("int")
                        .addParameterType("int a")
                        .addParameterType("int b")
                        .description("Adds two numbers")
                        .build())
                .build();

        TestGenerationResult result = agent.generateTests(metadata);

        Assertions.assertTrue(result.getAnalysis().contains("Calculator"));
        Assertions.assertFalse(result.getDraftTests().isBlank());
        Assertions.assertEquals(3, client.invocations.get());
        Assertions.assertFalse(result.getStreamedChunks().isEmpty());
    }

    private static class RecordingClient implements LLMClient {

        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public String sendPrompt(String prompt, Map<String, Object> options) {
            invocations.incrementAndGet();
            if (prompt.contains("analysis")) {
                return "Class analysed";
            }
            return "Refinement";
        }

        @Override
        public Stream<String> streamResponses(String prompt, Map<String, Object> options) {
            invocations.incrementAndGet();
            return Stream.of("public class CalculatorTest {}");
        }

        @Override
        public void handleRateLimits(int statusCode, String responseBody) {
            // no-op for tests
        }
    }
}
