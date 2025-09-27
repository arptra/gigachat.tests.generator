package com.acme.discount;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Set;

public class DiscountService {
    private static final double LOYALTY_DISCOUNT = 0.10;
    private final DiscountEngine discountEngine;

    public DiscountService() {
        this(defaultEngine());
    }

    public DiscountService(DiscountEngine discountEngine) {
        this.discountEngine = discountEngine;
    }

    public double applyDiscount(double amount, boolean loyalCustomer) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount must be non-negative");
        }
        if (loyalCustomer && amount > 0) {
            return round(amount * (1 - LOYALTY_DISCOUNT));
        }
        return round(amount);
    }

    public DiscountResult calculateDynamicDiscount(Order order, CustomerProfile customer, LocalDate calculationDate) {
        return discountEngine.calculate(order, customer, calculationDate);
    }

    private static DiscountEngine defaultEngine() {
        List<DiscountRule> rules = List.of(
                new LoyaltyDiscountRule(),
                new BulkOrderDiscountRule(ProductCategory.GROCERY, 8, 0.05),
                new SeasonalDiscountRule(List.of(
                        new SeasonalPromotion("Back to school electronics", Month.AUGUST, Month.SEPTEMBER,
                                Set.of(ProductCategory.ELECTRONICS), 0.08),
                        new SeasonalPromotion("Winter sports", Month.DECEMBER, Month.FEBRUARY,
                                Set.of(ProductCategory.SPORTS), 0.1),
                        new SeasonalPromotion("Spring home refresh", Month.MARCH, Month.APRIL,
                                Set.of(ProductCategory.HOME), 0.06))));
        return new DiscountEngine(rules);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
