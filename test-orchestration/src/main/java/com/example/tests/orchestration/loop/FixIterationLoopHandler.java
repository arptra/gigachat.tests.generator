package com.example.tests.orchestration.loop;

import com.example.tests.orchestration.gigachat.TestContextSnapshot;
import com.example.tests.orchestration.reporting.TestFailureDetail;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Placeholder handler that will contain custom logic for recovering from
 * repeated fix attempts that do not modify the test sources.
 */
public class FixIterationLoopHandler {

    public void handleLoop(TestContextSnapshot context, List<TestFailureDetail> failures) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(failures, "failures");
        System.out.printf(Locale.ENGLISH,
                "Loop detected for %s with %d unresolved failures.%n",
                context.getTestClassName(),
                failures.size());
    }
}
