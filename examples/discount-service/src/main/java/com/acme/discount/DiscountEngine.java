package com.acme.discount;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class DiscountEngine {
    private final List<DiscountRule> rules;

    public DiscountEngine(List<DiscountRule> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new IllegalArgumentException("At least one rule must be provided");
        }
        this.rules = List.copyOf(rules);
    }

    public DiscountResult calculate(Order order, CustomerProfile customer, LocalDate calculationDate) {
        if (order == null) {
            throw new IllegalArgumentException("Order must not be null");
        }
        if (customer == null) {
            throw new IllegalArgumentException("Customer must not be null");
        }
        if (calculationDate == null) {
            throw new IllegalArgumentException("Calculation date must not be null");
        }

        double subtotal = order.getSubtotal();
        double remaining = subtotal;
        List<AppliedDiscount> applied = new ArrayList<>();
        for (DiscountRule rule : rules) {
            DiscountOutcome outcome = rule.apply(order, customer, calculationDate);
            if (outcome.applied() && outcome.discountAmount() > 0) {
                double amount = Math.min(remaining, round(outcome.discountAmount()));
                if (amount > 0) {
                    remaining = Math.max(0, round(remaining - amount));
                    applied.add(new AppliedDiscount(rule.name(), amount, outcome.reason()));
                    if (remaining <= 0.0001) {
                        remaining = 0;
                        break;
                    }
                }
            }
        }
        return new DiscountResult(subtotal, remaining, applied);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
