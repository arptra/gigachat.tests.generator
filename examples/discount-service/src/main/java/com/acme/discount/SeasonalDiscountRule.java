package com.acme.discount;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class SeasonalDiscountRule implements DiscountRule {
    private final List<SeasonalPromotion> promotions;

    public SeasonalDiscountRule(List<SeasonalPromotion> promotions) {
        if (promotions == null || promotions.isEmpty()) {
            throw new IllegalArgumentException("At least one promotion must be provided");
        }
        this.promotions = List.copyOf(promotions);
    }

    @Override
    public DiscountOutcome apply(Order order, CustomerProfile customer, LocalDate calculationDate) {
        double discount = 0;
        List<String> appliedPromotions = new ArrayList<>();
        for (SeasonalPromotion promotion : promotions) {
            if (!promotion.isActive(calculationDate)) {
                continue;
            }
            double promotionSubtotal = order.getLines().stream()
                    .filter(line -> promotion.appliesTo(line.getCategory()))
                    .mapToDouble(OrderLine::getLineTotal)
                    .sum();
            if (promotionSubtotal > 0) {
                discount += promotionSubtotal * promotion.getPercentage();
                appliedPromotions.add(promotion.getName());
            }
        }
        if (discount <= 0) {
            return DiscountOutcome.skipped("No seasonal promotions applied");
        }
        String reason = "Promotions: " + appliedPromotions.stream().collect(Collectors.joining(", "));
        return DiscountOutcome.applied(discount, reason);
    }

    @Override
    public String name() {
        return "Seasonal promotion rule";
    }
}
