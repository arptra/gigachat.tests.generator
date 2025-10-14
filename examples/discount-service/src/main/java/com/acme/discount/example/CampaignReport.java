package com.acme.discount.example;

import java.util.List;
import java.util.Objects;

public record CampaignReport(String orderId,
                             String promotionName,
                             List<String> targetCategories,
                             List<String> reservedSkus,
                             boolean discountApplied,
                             double discountAmount,
                             String reason,
                             int loyaltyPointsAwarded,
                             String notificationChannel,
                             List<String> notes) {

    public CampaignReport {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(promotionName, "promotionName");
        targetCategories = List.copyOf(targetCategories);
        reservedSkus = List.copyOf(reservedSkus);
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(notificationChannel, "notificationChannel");
        notes = List.copyOf(notes);
    }
}
