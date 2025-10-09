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

        TestRunResult result = runner.runAllTests(request);
        List<TestFailureDetail> failures = collector.collectFailures(result);
        if (failures.isEmpty()) {
            return;
        }

        for (TestFailureDetail failure : failures) {
            TestContextSnapshot context = contexts.get(failure.getTestClass());
            if (context == null) {
                continue;
            }
            FixConversationSession session = fixGateway.startConversation(context, failure);
            List<String> exchanges = session.getExchanges();
            if (!exchanges.isEmpty()) {
                String latest = exchanges.get(exchanges.size() - 1);
                responseParser.extractJavaCode(latest)
                        .ifPresent(code -> applySafe(context, code));
            }
        }
    }

    private void applySafe(TestContextSnapshot context, String code) {
        try {
            fixApplier.applyFix(context, code);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to apply Gigachat fix", e);
        }
    }
}
