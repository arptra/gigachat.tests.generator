package com.example.tests.orchestration.gigachat;

import com.example.tests.orchestration.reporting.TestFailureDetail;

import java.util.List;
import java.util.Objects;

/**
 * Builds a prompt that combines the failing test diagnostics with the current test source and
 * domain API metadata so Gigachat can produce a focused fix.
 */
public final class GigachatFixRequestBuilder {

    public String buildFixPrompt(TestContextSnapshot context, TestFailureDetail failure) {
        Objects.requireNonNull(failure, "failure");
        return buildFixPrompt(context, List.of(failure));
    }

    public String buildFixPrompt(TestContextSnapshot context, List<TestFailureDetail> failures) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(failures, "failures");
        if (failures.isEmpty()) {
            throw new IllegalArgumentException("failures must not be empty");
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are assisting with repairing failing JUnit Jupiter tests for the class `")
                .append(context.getTestClassName()).append("`.\n");
        if (failures.size() == 1) {
            appendSingleFailureDescription(prompt, failures.get(0));
        } else {
            appendMultipleFailureDescription(prompt, failures);
        }

        prompt.append("Here is the current content of the test class. Keep the class public and avoid inventing additional domain types or placeholder enums.\n");
        prompt.append("```java\n").append(context.getSourceCode()).append("\n```\n\n");

        if (!context.getEnumConstants().isEmpty()) {
            prompt.append("Available enum constants:\n");
            context.getEnumConstants().forEach((enumName, constants) -> {
                prompt.append("- ").append(enumName).append('\n');
                constants.forEach(constant -> prompt.append("  - ").append(constant).append('\n'));
                prompt.append('\n');
            });
        }

        if (!context.getDependencyMethods().isEmpty()) {
            prompt.append("Documented dependency methods:\n");
            context.getDependencyMethods().forEach((type, members) -> {
                prompt.append("- ").append(type).append('\n');
                members.forEach(member -> prompt.append("  - ").append(member).append('\n'));
                prompt.append('\n');
            });
        }

        prompt.append("Please update only the shown test class so that it satisfies the documented APIs and addresses every failure described above.\n");
        prompt.append("Import every annotation and dependency you reference, keep existing dependencies intact, return the full revised Java file inside a ```java``` block, and describe any assumptions if required constants or APIs are still missing.\n");

        return prompt.toString();
    }

    private void appendSingleFailureDescription(StringBuilder prompt, TestFailureDetail failure) {
        prompt.append("The failing test is `").append(failure.getTestClass()).append('.')
                .append(failure.getTestMethod()).append("`.\n");
        prompt.append("Failure message: ").append(failure.getMessage()).append("\n\n");
        appendDiagnostics(prompt, failure.getDiagnostics());
    }

    private void appendMultipleFailureDescription(StringBuilder prompt, List<TestFailureDetail> failures) {
        prompt.append("The following failing tests were observed. Address all of them in the updated source.\n");
        for (int i = 0; i < failures.size(); i++) {
            TestFailureDetail failure = failures.get(i);
            prompt.append(i + 1).append(") `")
                    .append(failure.getTestClass()).append('.')
                    .append(failure.getTestMethod()).append("`\n");
            prompt.append("   Message: ").append(failure.getMessage()).append('\n');
            appendDiagnostics(prompt, failure.getDiagnostics());
            prompt.append('\n');
        }
    }

    private void appendDiagnostics(StringBuilder prompt, List<String> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return;
        }
        prompt.append("Diagnostics snippet:\n");
        diagnostics.forEach(line -> prompt.append(line).append('\n'));
        prompt.append('\n');
    }
}
