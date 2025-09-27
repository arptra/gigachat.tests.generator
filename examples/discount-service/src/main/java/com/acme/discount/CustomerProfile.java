package com.acme.discount;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class CustomerProfile {
    private final String customerId;
    private final LoyaltyTier loyaltyTier;
    private final int loyaltyPoints;
    private final LocalDate memberSince;
    private final Map<ProductCategory, Double> averageMonthlySpend;

    public CustomerProfile(String customerId,
                           LoyaltyTier loyaltyTier,
                           int loyaltyPoints,
                           LocalDate memberSince,
                           Map<ProductCategory, Double> averageMonthlySpend) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Customer id must be provided");
        }
        if (loyaltyTier == null) {
            throw new IllegalArgumentException("Loyalty tier must be provided");
        }
        if (memberSince == null) {
            throw new IllegalArgumentException("Member since date must be provided");
        }
        this.customerId = customerId;
        this.loyaltyTier = loyaltyTier;
        this.loyaltyPoints = Math.max(0, loyaltyPoints);
        this.memberSince = memberSince;
        if (averageMonthlySpend == null || averageMonthlySpend.isEmpty()) {
            this.averageMonthlySpend = Collections.emptyMap();
        } else {
            Map<ProductCategory, Double> copy = new EnumMap<>(ProductCategory.class);
            averageMonthlySpend.forEach((category, amount) -> {
                if (category != null && amount != null && amount >= 0) {
                    copy.put(category, amount);
                }
            });
            this.averageMonthlySpend = Map.copyOf(copy);
        }
    }

    public String getCustomerId() {
        return customerId;
    }

    public LoyaltyTier getLoyaltyTier() {
        return loyaltyTier;
    }

    public int getLoyaltyPoints() {
        return loyaltyPoints;
    }

    public LocalDate getMemberSince() {
        return memberSince;
    }

    public double getAverageMonthlySpend(ProductCategory category) {
        return averageMonthlySpend.getOrDefault(category, 0.0);
    }

    public boolean isAnniversaryMonth(LocalDate date) {
        return date != null && date.getMonth() == memberSince.getMonth();
    }

    public long getMembershipDurationInYears(LocalDate date) {
        if (date == null) {
            return 0;
        }
        return Math.max(0, ChronoUnit.YEARS.between(memberSince, date));
    }
}
