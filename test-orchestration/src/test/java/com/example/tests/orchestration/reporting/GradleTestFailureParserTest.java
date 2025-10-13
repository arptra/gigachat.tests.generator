package com.example.tests.orchestration.reporting;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradleTestFailureParserTest {

    @Test
    void extractsFailuresFromSummarySection() {
        String output = String.join("\n",
                "BulkOrderDiscountRuleTest > applies_discount_when_threshold_is_met() FAILED",
                "    org.opentest4j.AssertionFailedError: expected: <30.0> but was: <3000.0>",
                "        at com.acme.discount.BulkOrderDiscountRuleTest.applies_discount_when_threshold_is_met(BulkOrderDiscountRuleTest.java:49)",
                "",
                "CustomerProfileTest > shouldReturnCorrectCustomerId() FAILED",
                "    java.lang.NullPointerException: Cannot invoke \"Object.getClass()\" because \"key\" is null",
                "        at com.acme.discount.CustomerProfileTest.shouldReturnCorrectCustomerId(CustomerProfileTest.java:22)",
                "",
                "    > Task :test FAILED");

        GradleTestFailureParser parser = new GradleTestFailureParser();
        List<TestFailureDetail> failures = parser.parse(output);

        assertEquals(2, failures.size());
        TestFailureDetail first = failures.get(0);
        assertEquals("BulkOrderDiscountRuleTest", first.getTestClass());
        assertEquals("applies_discount_when_threshold_is_met()", first.getTestMethod());
        assertTrue(first.getMessage().contains("AssertionFailedError"));

        TestFailureDetail second = failures.get(1);
        assertEquals("CustomerProfileTest", second.getTestClass());
        assertTrue(second.getMessage().contains("NullPointerException"));
        assertEquals(3, second.getDiagnostics().size());
    }

    @Test
    void stripsAnsiEscapeSequencesBeforeParsing() {
        String output = String.join("\n",
                "\u001B[31mBulkOrderDiscountRuleTest > applies_discount_when_threshold_is_met() FAILED\u001B[0m",
                "    org.opentest4j.AssertionFailedError: expected: <30.0> but was: <3000.0>",
                "\u001B[0mCustomerProfileTest > shouldReturnCorrectCustomerId() FAILED",
                "    java.lang.NullPointerException: Cannot invoke \"Object.getClass()\" because \"key\" is null",
                "    > Task :test FAILED");

        GradleTestFailureParser parser = new GradleTestFailureParser();
        List<TestFailureDetail> failures = parser.parse(output);

        assertEquals(2, failures.size());
        assertEquals("BulkOrderDiscountRuleTest", failures.get(0).getTestClass());
        assertEquals("CustomerProfileTest", failures.get(1).getTestClass());
    }

    @Test
    void extractsCompilationErrorsWhenTestsDoNotRun() {
        String output = String.join("\n",
                "> Task :compileJava UP-TO-DATE",
                "> Task :processResources NO-SOURCE",
                "> Task :classes UP-TO-DATE",
                "",
                "> Task :compileTestJava FAILED",
                "/Users/acme/project/examples/discount-service/src/test/java/com/acme/discount/OrderTest.java:30: error: cannot find symbol",
                "        OrderLine line1 = createOrderLine(\"SKU001\", ProductCategory.FOOD, 3, 10.0);",
                "                                                                   ^",
                "  symbol:   variable FOOD",
                "  location: class ProductCategory",
                "/Users/acme/project/examples/discount-service/src/test/java/com/acme/discount/OrderTest.java:64: error: cannot find symbol",
                "        double foodSubtotal = order.getSubtotalForCategory(ProductCategory.FOOD);",
                "                                                                          ^",
                "  symbol:   variable FOOD",
                "  location: class ProductCategory",
                "",
                "FAILURE: Build failed with an exception.");

        GradleTestFailureParser parser = new GradleTestFailureParser();
        List<TestFailureDetail> failures = parser.parse(output);

        assertEquals(2, failures.size());

        TestFailureDetail first = failures.get(0);
        assertEquals("com.acme.discount.OrderTest", first.getTestClass());
        assertEquals("compileTestJava", first.getTestMethod());
        assertEquals("cannot find symbol", first.getMessage());
        assertEquals(5, first.getDiagnostics().size());

        TestFailureDetail second = failures.get(1);
        assertEquals("com.acme.discount.OrderTest", second.getTestClass());
        assertEquals("cannot find symbol", second.getMessage());
    }

    @Test
    void extractsMockitoFailuresWithoutSummary() {
        String output = String.join("\n",
                "> Task :test FAILED",
                "",
                "org.mockito.exceptions.base.MockitoException:",
                "Cannot instantiate @InjectMocks field named 'service' of type 'com.acme.discount.DiscountService'",
                "    at org.mockito.internal.configuration.InjectingAnnotationEngine.processInjectMocks(InjectingAnnotationEngine.java:42)",
                "    at com.acme.discount.OrderServiceTest.setUp(OrderServiceTest.java:27)",
                "",
                "1 test completed, 1 failed"
        );

        GradleTestFailureParser parser = new GradleTestFailureParser();
        List<TestFailureDetail> failures = parser.parse(output);

        assertEquals(1, failures.size());
        TestFailureDetail failure = failures.get(0);
        assertEquals("com.acme.discount.OrderServiceTest", failure.getTestClass());
        assertEquals("setUp()", failure.getTestMethod());
        assertTrue(failure.getMessage().contains("Cannot instantiate"));
        assertEquals(6, failure.getDiagnostics().size());
    }
}
