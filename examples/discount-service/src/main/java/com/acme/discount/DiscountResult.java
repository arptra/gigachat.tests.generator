package com.acme.discount;

import java.util.List;

public final class DiscountResult {
    private final double subtotal;
    private final double finalTotal;
    private final List<AppliedDiscount> appliedDiscounts;

    public DiscountResult(double subtotal, double finalTotal, List<AppliedDiscount> appliedDiscounts) {
        if (subtotal < 0 || finalTotal < 0) {
            throw new IllegalArgumentException("Amounts must be non-negative");
        }
        if (finalTotal > subtotal + 0.0001) {
            throw new IllegalArgumentException("Final total cannot exceed subtotal");
        }
        this.subtotal = round(subtotal);
        this.finalTotal = round(finalTotal);
        this.appliedDiscounts = List.copyOf(appliedDiscounts);
    }

    public double getSubtotal() {
        return subtotal;
    }

    public double getFinalTotal() {
        return finalTotal;
    }

    public double getTotalDiscount() {
        return round(subtotal - finalTotal);
    }

    public List<AppliedDiscount> getAppliedDiscounts() {
        return appliedDiscounts;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
