package com.example.tests.generator.prompt;

import com.example.tests.generator.metadata.ClassMetadata;
import com.example.tests.generator.metadata.CoverageRequirements;
import com.example.tests.generator.metadata.MethodMetadata;
import com.example.tests.generator.metadata.ParameterMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {

    @Test
    void buildPromptIncludesAllSections() {
        ClassMetadata metadata = ClassMetadata.builder()
                .packageName("com.example.service")
                .className("InvoiceService")
                .description("Handles invoice calculation and formatting")
                .addMethod(MethodMetadata.builder()
                        .name("calculate")
                        .returnType("BigDecimal")
                        .description("Computes invoice total using currency")
                        .addParameter(ParameterMetadata.builder().name("items").type("List<InvoiceItem>").build())
                        .build())
                .addDependency("com.example.currency.ExchangeClient")
                .coverageRequirements(CoverageRequirements.builder()
                        .lineCoverage(0.9)
                        .branchCoverage(0.75)
                        .addAdditionalCriterion("Cover negative currency conversion scenario")
                        .build())
                .addMockingRestriction("Avoid mocking value objects; prefer real instances")
                .addExampleScenario("Should fail when exchange service throws an error")
                .build();

        PromptBuilder promptBuilder = new PromptBuilder();

        String prompt = promptBuilder.buildPrompt(metadata);

        assertTrue(prompt.contains("InvoiceService"));
        assertTrue(prompt.contains("ExchangeClient"));
        assertTrue(prompt.contains("Line coverage: 90%"));
        assertTrue(prompt.contains("Branch coverage: 75%"));
        assertTrue(prompt.contains("Avoid mocking value objects"));
        assertTrue(prompt.contains("Example scenarios"));
        assertTrue(prompt.contains("Write JUnit Jupiter tests"));
    }

    @Test
    void augmentWithFeedbackAppendsIssues() {
        PromptBuilder promptBuilder = new PromptBuilder();
        String base = "base";

        String prompt = promptBuilder.augmentWithFeedback(base, List.of("Missing imports", "Compilation error"));

        assertTrue(prompt.contains("Previous attempt issues"));
        assertTrue(prompt.contains("Missing imports"));
        assertTrue(prompt.contains("Compilation error"));
        assertTrue(prompt.startsWith("base"));
    }
}
