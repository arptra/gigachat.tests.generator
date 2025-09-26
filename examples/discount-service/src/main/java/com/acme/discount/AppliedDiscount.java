package com.acme.discount;

public record AppliedDiscount(String ruleName, double amount, String description) {
    public AppliedDiscount {
        if (ruleName == null || ruleName.isBlank()) {
            throw new IllegalArgumentException("Rule name must be provided");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("Discount amount must be non-negative");
        }
        description = description == null ? "" : description;
    }
}
