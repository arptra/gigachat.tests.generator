package com.acme.discount.complex;

import com.acme.discount.DiscountOutcome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class CampaignMetricsCollector {
    private final String orderId;
    private final String promotionName;
    private RecommendationPipeline.Plan plan;
    private FraudDetectionService.FraudAlert fraudAlert;
    private DiscountOutcome discountOutcome;
    private DiscountOutcome fallbackOutcome;
    private LoyaltyLedger.LoyaltySnapshot loyaltySnapshot;
    private final List<String> notes = new ArrayList<>();

    public CampaignMetricsCollector(String orderId, String promotionName) {
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.promotionName = Objects.requireNonNull(promotionName, "promotionName");
    }

    public void recordRecommendationPlan(RecommendationPipeline.Plan plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        notes.add("Plan targets categories: " + plan.targetCategories());
    }

    public void recordReservation(InventoryGateway.InventoryReservation reservation) {
        Objects.requireNonNull(reservation, "reservation");
        notes.add("Reserved SKUs: " + reservation.reservedSkus());
    }

    public void recordFraud(FraudDetectionService.FraudAlert alert) {
        this.fraudAlert = Objects.requireNonNull(alert, "alert");
        notes.add("Fraud alert: " + alert.reason());
    }

    public void recordDiscount(DiscountOutcome outcome, com.acme.discount.DiscountResult result) {
        this.discountOutcome = Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(result, "result");
        notes.add("Discount outcome: " + outcome.reason());
        notes.add("Total discount applied: " + result.getTotalDiscount());
    }

    public void recordLoyalty(LoyaltyLedger.LoyaltySnapshot snapshot) {
        this.loyaltySnapshot = Objects.requireNonNull(snapshot, "snapshot");
        notes.add("Loyalty points awarded: " + snapshot.pointsAwarded());
    }

    public void recordNotification(NotificationGateway.NotificationReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt");
        notes.add("Notification channel: " + receipt.channel());
    }

    public CampaignReport buildReport(DiscountOutcome finalOutcome,
                                      InventoryGateway.InventoryReservation finalReservation,
                                      NotificationGateway.NotificationReceipt finalReceipt) {
        Objects.requireNonNull(finalOutcome, "finalOutcome");
        Objects.requireNonNull(finalReservation, "finalReservation");
        Objects.requireNonNull(finalReceipt, "finalReceipt");

        DiscountOutcome outcomeToUse = discountOutcome != null ? discountOutcome : finalOutcome;
        if (fraudAlert != null && outcomeToUse.applied()) {
            outcomeToUse = DiscountOutcome.skipped("Fraud override: " + fraudAlert.reason());
        }
        this.fallbackOutcome = outcomeToUse;

        List<String> immutableNotes = List.copyOf(notes);
        return new CampaignReport(orderId,
                promotionName,
                plan == null ? List.of() : plan.targetCategories(),
                finalReservation.reservedSkus(),
                outcomeToUse.applied(),
                outcomeToUse.discountAmount(),
                outcomeToUse.reason(),
                loyaltySnapshot == null ? 0 : loyaltySnapshot.pointsAwarded(),
                finalReceipt.channel(),
                immutableNotes);
    }

    public DiscountOutcome getFallbackOutcome() {
        return fallbackOutcome;
    }

    public List<String> getNotes() {
        return Collections.unmodifiableList(notes);
    }
}
