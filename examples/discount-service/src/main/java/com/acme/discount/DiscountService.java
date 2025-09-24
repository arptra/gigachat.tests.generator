package com.acme.discount;

public class DiscountService {
    private static final double LOYALTY_DISCOUNT = 0.10;

    public double applyDiscount(double amount, boolean loyalCustomer) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount must be non-negative");
        }
        if (loyalCustomer && amount > 0) {
            return round(amount * (1 - LOYALTY_DISCOUNT));
        }
        return round(amount);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
