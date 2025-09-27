package com.acme.discount;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public final class LoyaltyDiscountRule implements DiscountRule {
    private final Map<LoyaltyTier, Double> basePercentages;

    public LoyaltyDiscountRule() {
        basePercentages = new EnumMap<>(LoyaltyTier.class);
        basePercentages.put(LoyaltyTier.BRONZE, 0.0);
        basePercentages.put(LoyaltyTier.SILVER, 0.03);
        basePercentages.put(LoyaltyTier.GOLD, 0.06);
        basePercentages.put(LoyaltyTier.PLATINUM, 0.1);
    }

    @Override
    public DiscountOutcome apply(Order order, CustomerProfile customer, LocalDate calculationDate) {
        double basePercentage = basePercentages.getOrDefault(customer.getLoyaltyTier(), 0.0);
        if (basePercentage <= 0) {
            return DiscountOutcome.skipped("Customer loyalty tier does not qualify for base discount");
        }

        double bonus = calculateBonus(order, customer, calculationDate);
        double discount = order.getSubtotal() * (basePercentage + bonus);
        if (discount <= 0) {
            return DiscountOutcome.skipped("Calculated discount is zero");
        }
        String reason = String.format("Tier %s base %.1f%% + bonus %.1f%%",
                customer.getLoyaltyTier(), basePercentage * 100, bonus * 100);
        return DiscountOutcome.applied(discount, reason);
    }

    private double calculateBonus(Order order, CustomerProfile customer, LocalDate calculationDate) {
        double bonus = 0;
        int points = customer.getLoyaltyPoints();
        if (points >= 2000) {
            bonus += 0.03;
        } else if (points >= 1000) {
            bonus += 0.015;
        }

        if (customer.getMembershipDurationInYears(calculationDate) >= 5) {
            bonus += 0.01;
        }

        if (customer.isAnniversaryMonth(calculationDate)) {
            bonus += 0.02;
        }

        Optional<ProductCategory> dominantCategory = order.getDominantCategory();
        if (dominantCategory.isPresent()) {
            double averageSpend = customer.getAverageMonthlySpend(dominantCategory.get());
            if (averageSpend >= 750) {
                bonus += 0.01;
            }
        }
        return bonus;
    }

    @Override
    public String name() {
        return "Loyalty tier rule";
    }
}
