package com.acme.discount;

import java.time.LocalDate;

public final class BulkOrderDiscountRule implements DiscountRule {
    private final ProductCategory category;
    private final int thresholdQuantity;
    private final double percentage;

    public BulkOrderDiscountRule(ProductCategory category, int thresholdQuantity, double percentage) {
        if (category == null) {
            throw new IllegalArgumentException("Category must be provided");
        }
        if (thresholdQuantity <= 0) {
            throw new IllegalArgumentException("Threshold quantity must be positive");
        }
        if (percentage <= 0) {
            throw new IllegalArgumentException("Percentage must be positive");
        }
        this.category = category;
        this.thresholdQuantity = thresholdQuantity;
        this.percentage = percentage;
    }

    @Override
    public DiscountOutcome apply(Order order, CustomerProfile customer, LocalDate calculationDate) {
        int quantity = order.getQuantityForCategory(category);
        if (quantity < thresholdQuantity) {
            return DiscountOutcome.skipped("Bulk threshold not reached for " + category);
        }
        double subtotal = order.getSubtotalForCategory(category);
        double discount = subtotal * percentage;
        String reason = String.format("%d %s items trigger %.1f%% discount",
                quantity, category.name().toLowerCase(), percentage * 100);
        return DiscountOutcome.applied(discount, reason);
    }

    @Override
    public String name() {
        return "Bulk order rule";
    }
}
