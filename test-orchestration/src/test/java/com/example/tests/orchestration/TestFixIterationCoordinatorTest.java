package com.example.tests.orchestration;

import com.example.tests.orchestration.config.FixIterationExecutionSettings;
import com.example.tests.orchestration.execution.TestRunRequest;
import com.example.tests.orchestration.execution.TestRunResult;
import com.example.tests.orchestration.execution.TestSuiteRunner;
import com.example.tests.orchestration.fix.GigachatResponseParser;
import com.example.tests.orchestration.fix.TestFixApplier;
import com.example.tests.orchestration.gigachat.GigachatFixGateway;
import com.example.tests.orchestration.gigachat.GigachatFixRequestBuilder;
import com.example.tests.orchestration.gigachat.PromptClient;
import com.example.tests.orchestration.gigachat.TestContextSnapshot;
import com.example.tests.orchestration.loop.FixIterationLoopHandler;
import com.example.tests.orchestration.reporting.TestFailureCollector;
import com.example.tests.orchestration.reporting.TestFailureDetail;
import com.example.tests.orchestration.verification.TestSignatureVerifier;

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

        ScriptedRunner runner = new ScriptedRunner();
        runner.addResult(new TestRunResult(1, false, Duration.ZERO, failingOutput()));
        runner.addResult(new TestRunResult(0, false, Duration.ZERO, "BUILD SUCCESSFUL"));
        runner.addResult(new TestRunResult(0, false, Duration.ZERO, "All tests passed"));

        CapturingLoopHandler loopHandler = new CapturingLoopHandler();
        RecordingSignatureVerifier signatureVerifier = new RecordingSignatureVerifier();

        TestFixIterationCoordinator coordinator = new TestFixIterationCoordinator(
                runner,
                new TestFailureCollector(),
                gateway,
                new TestFixApplier(),
                new GigachatResponseParser(),
                loopHandler,
                FixIterationExecutionSettings.defaults(),
                signatureVerifier);

        Path tempSource = Files.createTempFile("OrderTest", ".java");
        tempSource.toFile().deleteOnExit();
        Map<String, TestContextSnapshot> contexts = new HashMap<>();
        contexts.put("com.acme.discount.OrderTest", new TestContextSnapshot(
                "com.acme.discount.OrderTest",
                tempSource,
                "public class OrderTest {}",
                Map.of(),
                Map.of(),
                Map.of(),
                List.of()));

        coordinator.executeAndAttemptFix(
                TestRunRequest.builder(Path.of(".")).build(),
                contexts);

        assertEquals(1, promptClient.prompts.size());
        assertEquals("public class OrderTest {}", promptClient.prompts.get(0).includedSource);

        assertEquals(3, runner.requests().size());
        assertEquals(List.of("test"), runner.requests().get(0).getTasks());
        assertEquals(List.of("compileTestJava"), runner.requests().get(1).getTasks());
        assertEquals(List.of("test"), runner.requests().get(2).getTasks());
        assertEquals(true, runner.requests().get(1).getAdditionalArguments().contains("--rerun-tasks"));
        assertEquals(true, runner.requests().get(2).getAdditionalArguments().contains("--rerun-tasks"));
        assertEquals(0, loopHandler.loopCount);
        assertEquals(List.of(List.of("com.acme.discount.OrderTest")), signatureVerifier.invocations);
    }

    @Test
    void delegatesToLoopHandlerWhenCodeDoesNotChange() throws Exception {
        RecordingPromptClient promptClient = new RecordingPromptClient();
        GigachatFixGateway gateway = new GigachatFixGateway(promptClient, new GigachatFixRequestBuilder());

        ScriptedRunner runner = new ScriptedRunner();
        runner.addResult(new TestRunResult(1, false, Duration.ZERO, failingOutput()));
        runner.addResult(new TestRunResult(1, false, Duration.ZERO, failingOutput()));

        CapturingLoopHandler loopHandler = new CapturingLoopHandler();
        RecordingSignatureVerifier signatureVerifier = new RecordingSignatureVerifier();

        TestFixIterationCoordinator coordinator = new TestFixIterationCoordinator(
                runner,
                new TestFailureCollector(),
                gateway,
                new TestFixApplier(),
                new GigachatResponseParser(),
                loopHandler,
                FixIterationExecutionSettings.defaults(),
                signatureVerifier);

        Path tempSource = Files.createTempFile("OrderTest", ".java");
        tempSource.toFile().deleteOnExit();
        Map<String, TestContextSnapshot> contexts = new HashMap<>();
        contexts.put("com.acme.discount.OrderTest", new TestContextSnapshot(
                "com.acme.discount.OrderTest",
                tempSource,
                "public class OrderTest {}",
                Map.of(),
                Map.of(),
                Map.of(),
                List.of()));

        coordinator.executeAndAttemptFix(
                TestRunRequest.builder(Path.of(".")).build(),
                contexts);

        assertEquals(2, promptClient.prompts.size());
        assertEquals(2, runner.requests().size());
        assertEquals(List.of("test"), runner.requests().get(0).getTasks());
        assertEquals(List.of("compileTestJava"), runner.requests().get(1).getTasks());
        assertEquals(1, loopHandler.loopCount);
        assertEquals(List.of(), signatureVerifier.invocations);
    }

    @Test
    void skipsCompilationWhenDisabled() throws Exception {
        RecordingPromptClient promptClient = new RecordingPromptClient();
        GigachatFixGateway gateway = new GigachatFixGateway(promptClient, new GigachatFixRequestBuilder());

        ScriptedRunner runner = new ScriptedRunner();
        runner.addResult(new TestRunResult(1, false, Duration.ZERO, failingOutput()));
        runner.addResult(new TestRunResult(0, false, Duration.ZERO, "All tests passed"));

        CapturingLoopHandler loopHandler = new CapturingLoopHandler();
        RecordingSignatureVerifier signatureVerifier = new RecordingSignatureVerifier();

        TestFixIterationCoordinator coordinator = new TestFixIterationCoordinator(
                runner,
                new TestFailureCollector(),
                gateway,
                new TestFixApplier(),
                new GigachatResponseParser(),
                loopHandler,
                new FixIterationExecutionSettings(false, true),
                signatureVerifier);

        Path tempSource = Files.createTempFile("OrderTest", ".java");
        tempSource.toFile().deleteOnExit();
        Map<String, TestContextSnapshot> contexts = new HashMap<>();
        contexts.put("com.acme.discount.OrderTest", new TestContextSnapshot(
                "com.acme.discount.OrderTest",
                tempSource,
                "public class OrderTest {}",
                Map.of(),
                Map.of(),
                Map.of(),
                List.of()));

        coordinator.executeAndAttemptFix(
                TestRunRequest.builder(Path.of(".")).build(),
                contexts);

        assertEquals(2, runner.requests().size());
        assertEquals(List.of("test"), runner.requests().get(0).getTasks());
        assertEquals(List.of("test"), runner.requests().get(1).getTasks());
        assertEquals(List.of(), signatureVerifier.invocations);
    }

    @Test
    void skipsExecutionWhenDisabled() throws Exception {
        RecordingPromptClient promptClient = new RecordingPromptClient();
        GigachatFixGateway gateway = new GigachatFixGateway(promptClient, new GigachatFixRequestBuilder());

        ScriptedRunner runner = new ScriptedRunner();
        runner.addResult(new TestRunResult(1, false, Duration.ZERO, failingOutput()));
        runner.addResult(new TestRunResult(0, false, Duration.ZERO, "BUILD SUCCESSFUL"));

        CapturingLoopHandler loopHandler = new CapturingLoopHandler();
        RecordingSignatureVerifier signatureVerifier = new RecordingSignatureVerifier();

        TestFixIterationCoordinator coordinator = new TestFixIterationCoordinator(
                runner,
                new TestFailureCollector(),
                gateway,
                new TestFixApplier(),
                new GigachatResponseParser(),
                loopHandler,
                new FixIterationExecutionSettings(true, false),
                signatureVerifier);

        Path tempSource = Files.createTempFile("OrderTest", ".java");
        tempSource.toFile().deleteOnExit();
        Map<String, TestContextSnapshot> contexts = new HashMap<>();
        contexts.put("com.acme.discount.OrderTest", new TestContextSnapshot(
                "com.acme.discount.OrderTest",
                tempSource,
                "public class OrderTest {}",
                Map.of(),
                Map.of(),
                Map.of(),
                List.of()));

        coordinator.executeAndAttemptFix(
                TestRunRequest.builder(Path.of(".")).build(),
                contexts);

        assertEquals(2, runner.requests().size());
        assertEquals(List.of("test"), runner.requests().get(0).getTasks());
        assertEquals(List.of("compileTestJava"), runner.requests().get(1).getTasks());
        assertEquals(List.of(List.of("com.acme.discount.OrderTest")), signatureVerifier.invocations);

        TestContextSnapshot snapshot = contexts.get("com.acme.discount.OrderTest");
        assertEquals("public class OrderTest {}", snapshot.getSourceCode());
    }

    @Test
    void updatesContextSourceWhenVerifierReturnsNewContent() throws Exception {
        RecordingPromptClient promptClient = new RecordingPromptClient();
        GigachatFixGateway gateway = new GigachatFixGateway(promptClient, new GigachatFixRequestBuilder());

        ScriptedRunner runner = new ScriptedRunner();
        runner.addResult(new TestRunResult(1, false, Duration.ZERO, failingOutput()));
        runner.addResult(new TestRunResult(0, false, Duration.ZERO, "BUILD SUCCESSFUL"));
        runner.addResult(new TestRunResult(0, false, Duration.ZERO, "All tests passed"));

        CapturingLoopHandler loopHandler = new CapturingLoopHandler();
        RecordingSignatureVerifier signatureVerifier = new RecordingSignatureVerifier();
        signatureVerifier.setResponse(Map.of(
                "com.acme.discount.OrderTest",
                "package com.acme.discount;\npublic class OrderTest { }"
        ));

        TestFixIterationCoordinator coordinator = new TestFixIterationCoordinator(
                runner,
                new TestFailureCollector(),
                gateway,
                new TestFixApplier(),
                new GigachatResponseParser(),
                loopHandler,
                FixIterationExecutionSettings.defaults(),
                signatureVerifier);

        Path tempSource = Files.createTempFile("OrderTest", ".java");
        tempSource.toFile().deleteOnExit();
        Map<String, TestContextSnapshot> contexts = new HashMap<>();
        contexts.put("com.acme.discount.OrderTest", new TestContextSnapshot(
                "com.acme.discount.OrderTest",
                tempSource,
                "public class OrderTest {}",
                Map.of(),
                Map.of(),
                Map.of(),
                List.of()));

        coordinator.executeAndAttemptFix(
                TestRunRequest.builder(Path.of(".")).build(),
                contexts);

        assertEquals(List.of(List.of("com.acme.discount.OrderTest")), signatureVerifier.invocations);
        TestContextSnapshot updated = contexts.get("com.acme.discount.OrderTest");
        assertEquals("package com.acme.discount;\npublic class OrderTest { }", updated.getSourceCode());
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
            return "```java\npublic class OrderTest {}\n```";
        }
    }

    private static final class RecordingSignatureVerifier implements TestSignatureVerifier {

        private final List<List<String>> invocations = new ArrayList<>();
        private Map<String, String> response = Map.of();

        @Override
        public Map<String, String> verifySignatures(java.util.Collection<String> testClassNames,
                                                    Map<String, TestContextSnapshot> contexts) {
            invocations.add(new ArrayList<>(testClassNames));
            return response;
        }

        void setResponse(Map<String, String> response) {
            this.response = response;
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

    private static final class ScriptedRunner implements TestSuiteRunner {
        private final List<TestRunResult> results = new ArrayList<>();
        private final List<TestRunRequest> requests = new ArrayList<>();
        private int index;

        private void addResult(TestRunResult result) {
            results.add(result);
        }

        private List<TestRunRequest> requests() {
            return requests;
        }

        @Override
        public TestRunResult runAllTests(TestRunRequest request) {
            requests.add(request);
            if (results.isEmpty()) {
                return new TestRunResult(0, false, Duration.ZERO, "");
            }
            if (index >= results.size()) {
                return results.get(results.size() - 1);
            }
            return results.get(index++);
        }
    }

    private static final class CapturingLoopHandler extends FixIterationLoopHandler {
        private int loopCount;

        @Override
        public void handleLoop(TestContextSnapshot context, List<TestFailureDetail> failures) {
            super.handleLoop(context, failures);
            loopCount++;
        }
    }
}
