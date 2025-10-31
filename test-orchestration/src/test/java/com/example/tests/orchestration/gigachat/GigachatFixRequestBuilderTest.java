package com.example.tests.orchestration.gigachat;

import com.example.tests.orchestration.reporting.TestFailureDetail;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GigachatFixRequestBuilderTest {

    @Test
    void embedsContextualInformationInPrompt() {
        TestContextSnapshot context = new TestContextSnapshot(
                "com.acme.discount.OrderTest",
                Paths.get("src/test/java/com/acme/discount/OrderTest.java"),
                "public class OrderTest {}",
                Map.of("com.acme.discount.Order", List.of(
                        "Order(String orderId, LocalDate orderDate, List<OrderLine> lines)",
                        "double getSubtotal()")),
                Map.of("com.acme.discount.CustomerProfile", List.of(
                        "CustomerProfile(String customerId, LoyaltyTier loyaltyTier, int loyaltyPoints, LocalDate memberSince, Map<ProductCategory, Double> averageMonthlySpend)")),
                Map.of("com.acme.discount.ProductCategory", List.of("GROCERY", "ELECTRONICS")),
                List.of("DiscountResult Order.calculate(CustomerProfile profile)")
        );

        TestFailureDetail failure = new TestFailureDetail(
                "OrderTest",
                "shouldCalculateSubtotal()",
                "org.opentest4j.AssertionFailedError: expected <30.0> but was <90.0>",
                List.of("Expected :30.0", "Actual   :90.0"));

        GigachatFixRequestBuilder builder = new GigachatFixRequestBuilder();
        String prompt = builder.buildFixPrompt(context, failure);

        assertTrue(prompt.contains("OrderTest"));
        assertTrue(prompt.contains("ProductCategory"));
        assertTrue(prompt.contains("GROCERY"));
        assertTrue(prompt.contains("Order(String orderId"));
        assertTrue(prompt.contains("CustomerProfile"));
        assertTrue(prompt.contains("```java"));
        assertTrue(prompt.contains("API reference reminders"));
        assertTrue(prompt.contains("DiscountResult Order.calculate"));
        assertTrue(prompt.contains("Do not use local variable type inference (`var`)"));
    }

    @Test
    void enumeratesMultipleFailuresInPrompt() {
        TestContextSnapshot context = new TestContextSnapshot(
                "com.acme.discount.OrderTest",
                Paths.get("src/test/java/com/acme/discount/OrderTest.java"),
                "public class OrderTest {}",
                Map.of(),
                Map.of(),
                Map.of(),
                List.of()
        );

        TestFailureDetail first = new TestFailureDetail(
                "OrderTest",
                "shouldCalculateSubtotal()",
                "Assertion failed",
                List.of("Expected :30.0", "Actual   :90.0"));

        TestFailureDetail second = new TestFailureDetail(
                "OrderTest",
                "shouldApplyDiscount()",
                "No interactions wanted",
                List.of());

        GigachatFixRequestBuilder builder = new GigachatFixRequestBuilder();
        String prompt = builder.buildFixPrompt(context, List.of(first, second));

        assertTrue(prompt.contains("1) `OrderTest.shouldCalculateSubtotal()`"));
        assertTrue(prompt.contains("2) `OrderTest.shouldApplyDiscount()`"));
    }

    @Test
    void suppressesHeavySectionsWhenMethodScopedPromptRequested() {
        TestContextSnapshot context = new TestContextSnapshot(
                "com.acme.discount.OrderTest",
                Paths.get("src/test/java/com/acme/discount/OrderTest.java"),
                "public class OrderTest {}",
                Map.of("com.acme.discount.Order", List.of(
                        "Order(String orderId, LocalDate orderDate, List<OrderLine> lines)")),
                Map.of("com.acme.discount.CustomerProfile", List.of(
                        "CustomerProfile(String customerId, LoyaltyTier loyaltyTier, int loyaltyPoints, LocalDate memberSince, Map<ProductCategory, Double> averageMonthlySpend)")),
                Map.of(),
                List.of("DiscountResult Order.calculate(CustomerProfile profile)"));

        TestFailureDetail failure = new TestFailureDetail(
                "OrderTest",
                "shouldCalculateSubtotal()",
                "Assertion failed",
                List.of("Expected :30.0", "Actual   :90.0"));

        GigachatFixRequestBuilder builder = new GigachatFixRequestBuilder();
        String prompt = builder.buildFixPrompt(context, List.of(failure), true);

        assertTrue(prompt.contains("Only update the listed failing test methods"));
        assertTrue(prompt.contains("API reference reminders"));
        assertTrue(prompt.contains("Dependency documentation omitted for brevity"));
        assertFalse(prompt.contains("Documented dependency methods"));
        assertFalse(prompt.contains("Supporting types:"));
    }
}
