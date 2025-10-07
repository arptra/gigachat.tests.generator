package com.example.tests.generator.prompt;

import com.example.tests.generator.metadata.ClassMetadata;
import com.example.tests.generator.metadata.CoverageRequirements;
import com.example.tests.generator.metadata.MethodMetadata;
import com.example.tests.generator.metadata.ParameterMetadata;
import com.example.tests.generator.metadata.RelatedTypeMetadata;
import com.example.tests.generator.model.ClassKind;
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
        assertTrue(prompt.contains("public class InvoiceServiceTest"));
        assertTrue(prompt.contains("@ExtendWith(MockitoExtension.class)"));
    }

    @Test
    void buildPromptHighlightsMissingEnumConstants() {
        ClassMetadata metadata = ClassMetadata.builder()
                .packageName("com.example")
                .className("OrderService")
                .addSupportingType(RelatedTypeMetadata.builder()
                        .qualifiedName("com.example.ProductCategory")
                        .className("ProductCategory")
                        .kind(ClassKind.ENUM)
                        .build())
                .build();

        PromptBuilder promptBuilder = new PromptBuilder();

        String prompt = promptBuilder.buildPrompt(metadata);

        assertTrue(prompt.contains("ProductCategory"));
        assertTrue(prompt.contains("Enum constants were not documented"));
        assertTrue(prompt.contains("pause and ask"));
        assertTrue(prompt.contains("do not invent placeholder enums"));
    }

    @Test
    void augmentWithFeedbackAppendsIssues() {
        PromptBuilder promptBuilder = new PromptBuilder();
        String base = "base";

        String prompt = promptBuilder.augmentWithFeedback(base, List.of("Missing imports", "Compilation error"));

        assertTrue(prompt.contains("Previous attempt issues"));
        assertTrue(prompt.contains("Missing imports"));
        assertTrue(prompt.contains("Compilation error"));
        assertTrue(prompt.contains("clarifying question"));
        assertTrue(prompt.contains("Provide a short checklist"));
        assertTrue(prompt.contains("Follow-up strategy"));
        assertTrue(prompt.contains("```java"));
        assertTrue(prompt.startsWith("base"));
    }

    @Test
    void augmentWithFeedbackSuggestsRequestingEnumConstants() {
        PromptBuilder promptBuilder = new PromptBuilder();
        String prompt = promptBuilder.augmentWithFeedback("base", List.of(
                "ProductCategory does not declare enum constant ELECTRONICS. Use one of: constants not documented—ask for the declared values before using them"
        ));

        assertTrue(prompt.contains("Request the declared enum constants for ProductCategory"));
    }

    @Test
    void augmentWithFeedbackHighlightsRepeatedFailures() {
        PromptBuilder promptBuilder = new PromptBuilder();
        String prompt = promptBuilder.augmentWithFeedback("base", List.of(
                "Missing required imports for JUnit Jupiter or Mockito.",
                "Missing required imports for JUnit Jupiter or Mockito."
        ));

        assertTrue(prompt.contains("You are repeating the same failures"));
        assertTrue(prompt.contains("Ensure the imports include"));
    }

    @Test
    void augmentWithFeedbackMentionsUnresolvedTypes() {
        PromptBuilder promptBuilder = new PromptBuilder();
        String prompt = promptBuilder.augmentWithFeedback("base", List.of(
                "Type UnknownService is unresolved. Import the correct package or declare a minimal helper inside the test file before using it."
        ));

        assertTrue(prompt.contains("UnknownService"));
        assertTrue(prompt.contains("Clarify or request definitions"));
    }
}
