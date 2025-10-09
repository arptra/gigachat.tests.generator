package com.example.tests.orchestration.gigachat;

import com.example.tests.orchestration.reporting.TestFailureDetail;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

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
                Map.of("com.acme.discount.ProductCategory", List.of("GROCERY", "ELECTRONICS"))
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
        assertTrue(prompt.contains("```java"));
    }
}
