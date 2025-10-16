package com.example.tests.generator.prompt;

import com.example.tests.generator.metadata.ClassMetadata;
import com.example.tests.generator.metadata.CoverageRequirements;
import com.example.tests.generator.metadata.MethodMetadata;
import com.example.tests.generator.metadata.ParameterMetadata;
import com.example.tests.generator.metadata.RelatedTypeMetadata;
import com.example.tests.generator.model.ClassKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void augmentWithFeedbackIncludesCodeAndApiReference() {
        PromptBuilder promptBuilder = new PromptBuilder();
        ClassMetadata metadata = ClassMetadata.builder()
                .packageName("com.acme.discount")
                .className("Order")
                .addMethod(MethodMetadata.builder()
                        .name("getSubtotal")
                        .returnType("double")
                        .build())
                .addMethod(MethodMetadata.builder()
                        .name("getDominantCategory")
                        .returnType("java.util.Optional<ProductCategory>")
                        .build())
                .addMethod(MethodMetadata.builder()
                        .name("Order")
                        .returnType("void")
                        .constructor(true)
                        .addParameter(ParameterMetadata.builder().name("orderId").type("String").build())
                        .addParameter(ParameterMetadata.builder().name("orderDate").type("LocalDate").build())
                        .addParameter(ParameterMetadata.builder().name("lines").type("List<OrderLine>").build())
                        .build())
                .addSupportingType(RelatedTypeMetadata.builder()
                        .qualifiedName("com.acme.discount.ProductCategory")
                        .className("ProductCategory")
                        .kind(ClassKind.ENUM)
                        .build())
                .build();

        metadata = metadata.withDependencyDocumentation(
                Map.of("com.acme.discount.CustomerProfile", List.of("CustomerProfile(String customerId, LoyaltyTier loyaltyTier, int loyaltyPoints, LocalDate memberSince, Map<ProductCategory, Double> averageMonthlySpend)")),
                Map.of("com.acme.discount.LoyaltyTier", List.of("enum constants: BASIC, SILVER, GOLD")),
                List.of(),
                false
        );

        String code = "package com.acme.discount;\npublic class OrderTest {}";

        String prompt = promptBuilder.augmentWithFeedback("base", List.of("Compilation failed"), code, metadata);

        assertTrue(prompt.contains("Latest attempt under review"));
        assertTrue(prompt.contains("```java"));
        assertTrue(prompt.contains("public class OrderTest"));
        assertTrue(prompt.contains("Order(String orderId, LocalDate orderDate, List<OrderLine> lines)"));
        assertTrue(prompt.contains("double Order.getSubtotal()"));
        assertTrue(prompt.contains("Documented dependency methods"));
        assertTrue(prompt.contains("Supporting types"));
        assertTrue(prompt.contains("Enum constants not documented"));
    }

    @Test
    void buildPromptOmitsUnusedMembersWhenDependencyFocusEnabled() {
        ClassMetadata metadata = ClassMetadata.builder()
                .packageName("com.example.focus")
                .className("FocusedService")
                .description("Coordinates focused processing")
                .addMethod(MethodMetadata.builder()
                        .name("FocusedService")
                        .returnType("void")
                        .constructor(true)
                        .addParameter(ParameterMetadata.builder().name("id").type("String").build())
                        .addParameter(ParameterMetadata.builder().name("collaborator").type("CollaboratorService").build())
                        .build())
                .addMethod(MethodMetadata.builder()
                        .name("run")
                        .returnType("void")
                        .description("Triggers the workflow")
                        .build())
                .addMethod(MethodMetadata.builder()
                        .name("unusedHelper")
                        .returnType("void")
                        .description("Legacy hook")
                        .build())
                .addSupportingType(RelatedTypeMetadata.builder()
                        .qualifiedName("com.example.focus.dto.Customer")
                        .className("Customer")
                        .addMethod(MethodMetadata.builder()
                                .name("Customer")
                                .constructor(true)
                                .returnType("void")
                                .addParameter(ParameterMetadata.builder().name("id").type("String").build())
                                .build())
                        .addMethod(MethodMetadata.builder()
                                .name("getName")
                                .returnType("String")
                                .build())
                        .addMethod(MethodMetadata.builder()
                                .name("getInternalId")
                                .returnType("String")
                                .build())
                        .build())
                .build();

        metadata = metadata.withDependencyDocumentation(
                Map.of("com.example.focus.CollaboratorService", List.of("CollaboratorService()", "void CollaboratorService.execute()")),
                Map.of("com.example.focus.dto.Customer", List.of(
                        "Customer(String id)",
                        "String Customer.getName()"
                )),
                List.of(
                        "FocusedService(String id, CollaboratorService collaborator)",
                        "void FocusedService.run()"
                ),
                true
        );

        String prompt = new PromptBuilder().buildPrompt(metadata);

        assertTrue(prompt.contains("void FocusedService.run()"));
        assertFalse(prompt.contains("unusedHelper"));
        assertTrue(prompt.contains("Customer(String id)"));
        assertTrue(prompt.contains("String Customer.getName()"));
        assertFalse(prompt.contains("getInternalId"));
    }

    @Test
    void buildPromptOmitsUndocumentedDependenciesWhenDependencyFocusEnabled() {
        ClassMetadata metadata = ClassMetadata.builder()
                .packageName("com.example.focus")
                .className("FocusedService")
                .addMethod(MethodMetadata.builder()
                        .name("run")
                        .returnType("void")
                        .build())
                .addDependency("com.example.focus.DepA")
                .addDependency("com.example.focus.DepB")
                .build();

        metadata = metadata.withDependencyDocumentation(
                Map.of("com.example.focus.DepB", List.of("void DepB.execute()")),
                Map.of(),
                List.of("void FocusedService.run()"),
                true
        );

        String prompt = new PromptBuilder().buildPrompt(metadata);

        assertTrue(prompt.contains("com.example.focus.DepB"));
        assertFalse(prompt.contains("com.example.focus.DepA"));
    }

    @Test
    void buildPromptExplainsMissingFocusedDependencies() {
        ClassMetadata metadata = ClassMetadata.builder()
                .packageName("com.example.focus")
                .className("FocusedService")
                .addMethod(MethodMetadata.builder()
                        .name("run")
                        .returnType("void")
                        .build())
                .addDependency("com.example.focus.DepA")
                .build();

        metadata = metadata.withDependencyDocumentation(
                Map.of(),
                Map.of(),
                List.of("void FocusedService.run()"),
                true
        );

        String prompt = new PromptBuilder().buildPrompt(metadata);

        assertTrue(prompt.contains("The dependency analysis did not report any collaborator methods for the focused scope."));
        assertFalse(prompt.contains("com.example.focus.DepA"));
    }

    @Test
    void buildPromptOmitsUndocumentedSupportingTypesInFocusMode() {
        RelatedTypeMetadata documented = RelatedTypeMetadata.builder()
                .qualifiedName("com.example.focus.dto.Customer")
                .className("Customer")
                .addMethod(MethodMetadata.builder()
                        .name("Customer")
                        .constructor(true)
                        .returnType("void")
                        .build())
                .build();

        RelatedTypeMetadata skipped = RelatedTypeMetadata.builder()
                .qualifiedName("com.example.focus.dto.Address")
                .className("Address")
                .addMethod(MethodMetadata.builder()
                        .name("getCity")
                        .returnType("String")
                        .build())
                .build();

        ClassMetadata metadata = ClassMetadata.builder()
                .packageName("com.example.focus")
                .className("FocusedService")
                .addMethod(MethodMetadata.builder()
                        .name("run")
                        .returnType("void")
                        .build())
                .addSupportingType(documented)
                .addSupportingType(skipped)
                .build();

        metadata = metadata.withDependencyDocumentation(
                Map.of("com.example.focus.Helper", List.of("void Helper.assist()")),
                Map.of("com.example.focus.dto.Customer", List.of("Customer()")),
                List.of("void FocusedService.run()"),
                true
        );

        String prompt = new PromptBuilder().buildPrompt(metadata);

        assertTrue(prompt.contains("com.example.focus.dto.Customer"));
        assertFalse(prompt.contains("com.example.focus.dto.Address"));
    }
}
