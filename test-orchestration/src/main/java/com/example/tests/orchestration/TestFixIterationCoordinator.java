package com.example.tests.orchestration;

import com.example.tests.orchestration.execution.TestRunRequest;
import com.example.tests.orchestration.execution.TestRunResult;
import com.example.tests.orchestration.execution.TestSuiteRunner;
import com.example.tests.orchestration.fix.GigachatResponseParser;
import com.example.tests.orchestration.fix.TestFixApplier;
import com.example.tests.orchestration.gigachat.FixConversationSession;
import com.example.tests.orchestration.gigachat.GigachatFixGateway;
import com.example.tests.orchestration.gigachat.TestContextSnapshot;
import com.example.tests.orchestration.reporting.TestFailureCollector;
import com.example.tests.orchestration.reporting.TestFailureDetail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Coordinates the end-to-end flow: run tests, collect failures, ask Gigachat for fixes,
 * and apply the returned changes. The design supports future multi-iteration refinement
 * by keeping the conversation session alive between runs.
 */
public final class TestFixIterationCoordinator {

    private final TestSuiteRunner runner;
    private final TestFailureCollector collector;
    private final GigachatFixGateway fixGateway;
    private final TestFixApplier fixApplier;
    private final GigachatResponseParser responseParser;

    public TestFixIterationCoordinator(TestSuiteRunner runner,
                                       TestFailureCollector collector,
                                       GigachatFixGateway fixGateway,
                                       TestFixApplier fixApplier,
                                       GigachatResponseParser responseParser) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.collector = Objects.requireNonNull(collector, "collector");
        this.fixGateway = Objects.requireNonNull(fixGateway, "fixGateway");
        this.fixApplier = Objects.requireNonNull(fixApplier, "fixApplier");
        this.responseParser = Objects.requireNonNull(responseParser, "responseParser");
    }

    public void executeAndAttemptFix(TestRunRequest request, Map<String, TestContextSnapshot> contexts) {
        Objects.requireNonNull(contexts, "contexts");

        TestRunResult initialResult = runner.runAllTests(request);
        List<TestFailureDetail> failures = collector.collectFailures(initialResult);
        if (failures.isEmpty()) {
            return;
        }

        TestRunRequest compileRequest = buildRequest(request, List.of("compileTestJava"));
        TestRunRequest verificationRequest = buildRequest(request, List.of("test"));

        while (!failures.isEmpty()) {
            if (!applyFixesForFailures(contexts, failures)) {
                return;
            }

            TestRunResult compileResult = runner.runAllTests(compileRequest);
            if (!compileResult.isSuccessful()) {
                System.out.println("Test compilation failed. Returning to fix step.");
                failures = collector.collectFailures(compileResult);
                if (failures.isEmpty()) {
                    return;
                }
                continue;
            }
            System.out.println("Test compilation succeeded.");

            TestRunResult verificationResult = runner.runAllTests(verificationRequest);
            if (!verificationResult.isSuccessful()) {
                System.out.println("Test execution failed. Returning to fix step.");
                failures = collector.collectFailures(verificationResult);
                if (failures.isEmpty()) {
                    return;
                }
                continue;
            }

            System.out.println("Tests passed successfully.");
            return;
        }
    }

    private boolean applyFixesForFailures(Map<String, TestContextSnapshot> contexts, List<TestFailureDetail> failures) {
        Map<TestContextSnapshot, List<TestFailureDetail>> grouped = new LinkedHashMap<>();
        for (TestFailureDetail failure : failures) {
            TestContextSnapshot context = resolveContext(contexts, failure);
            if (context == null) {
                continue;
            }
            grouped.computeIfAbsent(context, key -> new ArrayList<>()).add(failure);
        }

        if (grouped.isEmpty()) {
            return false;
        }

        for (Map.Entry<TestContextSnapshot, List<TestFailureDetail>> entry : grouped.entrySet()) {
            TestContextSnapshot context = entry.getKey();
            List<TestFailureDetail> groupedFailures = entry.getValue();
            if (groupedFailures.size() == 1) {
                applySingleFailureFix(context, groupedFailures.get(0));
            } else {
                applyGroupedFailureFix(context, groupedFailures);
            }
        }

        return true;
    }

    private void applySingleFailureFix(TestContextSnapshot context, TestFailureDetail failure) {
        FixConversationSession session = fixGateway.startConversation(context, failure);
        applyLatestResponse(context, session);
    }

    private void applyGroupedFailureFix(TestContextSnapshot context, List<TestFailureDetail> failures) {
        FixConversationSession session = fixGateway.startConversation(context, failures);
        applyLatestResponse(context, session);
    }

    private void applyLatestResponse(TestContextSnapshot context, FixConversationSession session) {
        List<String> exchanges = session.getExchanges();
        if (exchanges.isEmpty()) {
            return;
        }
        String latest = exchanges.get(exchanges.size() - 1);
        responseParser.extractJavaCode(latest)
                .ifPresent(code -> applySafe(context, code));
    }

    private TestRunRequest buildRequest(TestRunRequest template, List<String> tasks) {
        TestRunRequest.Builder builder = TestRunRequest.builder(template.getProjectDir())
                .gradleExecutable(template.getGradleExecutable())
                .tasks(tasks)
                .timeout(template.getTimeout());
        template.getAdditionalArguments().forEach(builder::addArgument);
        if (!template.getAdditionalArguments().contains("--rerun-tasks")) {
            builder.addArgument("--rerun-tasks");
        }
        template.getEnvironment().forEach(builder::addEnvironmentVariable);
        return builder.build();
    }

    private TestContextSnapshot resolveContext(Map<String, TestContextSnapshot> contexts, TestFailureDetail failure) {
        String testClass = failure.getTestClass();
        TestContextSnapshot context = contexts.get(testClass);
        if (context != null) {
            return context;
        }

        String simpleName = simpleName(testClass);
        TestContextSnapshot match = null;
        for (Map.Entry<String, TestContextSnapshot> entry : contexts.entrySet()) {
            if (simpleName(entry.getKey()).equals(simpleName)) {
                if (match != null) {
                    return null;
                }
                match = entry.getValue();
            }
        }

        if (match != null) {
            return match;
        }

        return contexts.get(simpleName);
    }

    private static String simpleName(String className) {
        int lastDot = className.lastIndexOf('.');
        return lastDot >= 0 ? className.substring(lastDot + 1) : className;
    }

    private void applySafe(TestContextSnapshot context, String code) {
        try {
            fixApplier.applyFix(context, code);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to apply Gigachat fix", e);
        }
    }
}
