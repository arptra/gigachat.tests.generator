package com.example.tests.generator.prompts;

import com.example.tests.generator.model.ClassMetadata;
import com.example.tests.generator.model.MethodMetadata;
import java.util.stream.Collectors;

/**
 * Builds human readable prompts for the Gigachat agent from metadata objects.
 */
public class PromptFactory {

    public String buildAnalysisPrompt(ClassMetadata metadata) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are analysing the following Java class to generate unit tests.\n");
        if (metadata.getPackageName() != null && !metadata.getPackageName().isBlank()) {
            builder.append("Class: ").append(metadata.getPackageName()).append(".");
        } else {
            builder.append("Class: ");
        }
        builder.append(metadata.getClassName()).append("\n\n");
        if (metadata.getDescription() != null) {
            builder.append("Description: ").append(metadata.getDescription()).append("\n\n");
        }
        if (!metadata.getMethods().isEmpty()) {
            builder.append("Methods:\n");
            for (MethodMetadata method : metadata.getMethods()) {
                builder.append("- ").append(method.getReturnType())
                        .append(" ").append(method.getName())
                        .append("(")
                        .append(method.getParameterTypes().stream().collect(Collectors.joining(", ")))
                        .append(")");
                if (method.isStatic()) {
                    builder.append(" [static]");
                }
                if (method.getDescription() != null) {
                    builder.append(" - ").append(method.getDescription());
                }
                builder.append("\n");
            }
            builder.append("\n");
        }
        if (!metadata.getDependencies().isEmpty()) {
            builder.append("Important collaborators: ")
                    .append(String.join(", ", metadata.getDependencies()))
                    .append("\n\n");
        }
        builder.append("Provide a short analysis highlighting potential edge cases and observable behaviour.");
        return builder.toString();
    }

    public String buildTestDraftPrompt(ClassMetadata metadata, String analysis) {
        StringBuilder builder = new StringBuilder();
        builder.append("You previously performed the following analysis:\n")
                .append(analysis).append("\n\n");
        builder.append("Now craft a JUnit 5 test class for ")
                .append(metadata.getClassName())
                .append(". Focus on meaningful assertions, negative scenarios and document any assumptions.\n");
        builder.append("Return the tests as compilable Java code wrapped inside a ```java fenced block.");
        return builder.toString();
    }

    public String buildRefinementPrompt(ClassMetadata metadata, String generatedTests) {
        StringBuilder builder = new StringBuilder();
        builder.append("Review the generated tests for the class ")
                .append(metadata.getClassName()).append(" and propose improvements.")
                .append(" Ensure mocks or stubs are suggested when collaborators are present.\n")
                .append("Existing draft:\n")
                .append(generatedTests);
        builder.append("\nProvide a concise summary of required refactorings.");
        return builder.toString();
    }
}
