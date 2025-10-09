package com.example.tests.orchestration.gigachat;

import com.example.tests.orchestration.reporting.TestFailureDetail;

import java.util.Objects;
import java.util.StringJoiner;

/**
 * Builds a prompt that combines the failing test diagnostics with the current test source and
 * domain API metadata so Gigachat can produce a focused fix.
 */
public final class GigachatFixRequestBuilder {

    public String buildFixPrompt(TestContextSnapshot context, TestFailureDetail failure) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(failure, "failure");

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are assisting with repairing a failing JUnit Jupiter test.\n");
        prompt.append("The failing test is `").append(failure.getTestClass()).append('.')
                .append(failure.getTestMethod()).append("`.\n");
        prompt.append("Failure message: ").append(failure.getMessage()).append("\n\n");

        if (!failure.getDiagnostics().isEmpty()) {
            prompt.append("Diagnostics snippet:\n");
            failure.getDiagnostics().forEach(line -> prompt.append(line).append('\n'));
            prompt.append('\n');
        }

        prompt.append("Here is the current content of the test class. Keep the class public and avoid inventing additional domain types or placeholder enums.\n");
        prompt.append("```java\n").append(context.getSourceCode()).append("\n```\n\n");

        if (!context.getEnumConstants().isEmpty()) {
            prompt.append("Available enum constants:\n");
            context.getEnumConstants().forEach((enumName, constants) -> {
                prompt.append("- ").append(enumName).append(':');
                StringJoiner joiner = new StringJoiner(", ", " [", "]\n");
                constants.forEach(joiner::add);
                prompt.append(joiner.toString());
            });
            prompt.append('\n');
        }

        if (!context.getDependencyMethods().isEmpty()) {
            prompt.append("Documented dependency methods:\n");
            context.getDependencyMethods().forEach((type, methods) -> {
                prompt.append("- ").append(type).append(':');
                StringJoiner joiner = new StringJoiner("; ", " ", "\n");
                methods.forEach(joiner::add);
                prompt.append(joiner.toString());
            });
            prompt.append('\n');
        }

        prompt.append("Please update only the shown test class so that it satisfies the documented APIs and addresses the failure.\n");
        prompt.append("Return the full revised Java file inside a ```java``` block and describe any assumptions if required constants or APIs are still missing.\n");

        return prompt.toString();
    }
}
