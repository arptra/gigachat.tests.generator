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
}
