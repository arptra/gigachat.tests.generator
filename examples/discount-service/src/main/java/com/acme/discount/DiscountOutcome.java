package com.acme.discount;

public record DiscountOutcome(boolean applied, double discountAmount, String reason) {
    public DiscountOutcome {
        if (discountAmount < 0) {
            throw new IllegalArgumentException("Discount amount must be non-negative");
        }
        reason = reason == null ? "" : reason;
    }

    public static DiscountOutcome applied(double amount, String reason) {
        return new DiscountOutcome(true, amount, reason);
    }

    public static DiscountOutcome skipped(String reason) {
        return new DiscountOutcome(false, 0, reason);
    }
}
