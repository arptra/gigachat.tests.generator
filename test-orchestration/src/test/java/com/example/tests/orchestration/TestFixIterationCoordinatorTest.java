package com.example.tests.orchestration;

import com.example.tests.orchestration.execution.TestRunRequest;
import com.example.tests.orchestration.execution.TestRunResult;
import com.example.tests.orchestration.fix.GigachatResponseParser;
import com.example.tests.orchestration.fix.TestFixApplier;
import com.example.tests.orchestration.gigachat.GigachatFixGateway;
import com.example.tests.orchestration.gigachat.GigachatFixRequestBuilder;
import com.example.tests.orchestration.gigachat.PromptClient;
import com.example.tests.orchestration.gigachat.TestContextSnapshot;
import com.example.tests.orchestration.reporting.TestFailureCollector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestFixIterationCoordinatorTest {

    @Test
    void resolvesContextBySimpleClassNameWhenFullyQualifiedEntryExists() throws Exception {
        RecordingPromptClient promptClient = new RecordingPromptClient();
        GigachatFixGateway gateway = new GigachatFixGateway(promptClient, new GigachatFixRequestBuilder());

        TestFixIterationCoordinator coordinator = new TestFixIterationCoordinator(
                request -> new TestRunResult(1, false, Duration.ZERO, failingOutput()),
                new TestFailureCollector(),
                gateway,
                new TestFixApplier(),
                new GigachatResponseParser());

        Path tempSource = Files.createTempFile("OrderTest", ".java");
        tempSource.toFile().deleteOnExit();
        Map<String, TestContextSnapshot> contexts = new HashMap<>();
        contexts.put("com.acme.discount.OrderTest", new TestContextSnapshot(
                "com.acme.discount.OrderTest",
                tempSource,
                "public class OrderTest {}",
                Map.of(),
                Map.of()));

        coordinator.executeAndAttemptFix(
                TestRunRequest.builder(Path.of(".")).build(),
                contexts);

        assertEquals(1, promptClient.prompts.size());
        assertEquals("public class OrderTest {}", promptClient.prompts.get(0).includedSource);
    }

    private static String failingOutput() {
        return String.join("\n",
                "OrderTest > failingTest FAILED",
                "    java.lang.AssertionError: boom");
    }

    private static final class RecordingPromptClient implements PromptClient {
        private final List<RecordedPrompt> prompts = new ArrayList<>();

        @Override
        public String sendPrompt(String prompt, Map<String, Object> options) {
            prompts.add(new RecordedPrompt(prompt));
            return "no code";
        }
    }

    private static final class RecordedPrompt {
        private final String includedSource;

        private RecordedPrompt(String prompt) {
            int start = prompt.indexOf("```java\n");
            if (start >= 0) {
                int end = prompt.indexOf("```", start + 1);
                if (end > start) {
                    includedSource = prompt.substring(start + "```java\n".length(), end).strip();
                    return;
                }
            }
            includedSource = "";
        }
    }
}
