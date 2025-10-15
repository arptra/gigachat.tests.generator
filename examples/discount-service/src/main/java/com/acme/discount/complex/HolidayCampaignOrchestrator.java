package com.acme.discount.complex;

import com.acme.discount.CustomerProfile;
import com.acme.discount.DiscountOutcome;
import com.acme.discount.DiscountEngine;
import com.acme.discount.Order;
import com.acme.discount.SeasonalPromotion;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A complex example showcasing an orchestration method with several internal dependencies
 * that need to be initialized before use.
 */
public class HolidayCampaignOrchestrator {
    private final DiscountEngine discountEngine;
    private final InventoryGateway inventoryGateway;
    private final FraudDetectionService fraudDetectionService;
    private final LoyaltyLedger loyaltyLedger;
    private final NotificationGateway notificationGateway;

    public HolidayCampaignOrchestrator(DiscountEngine discountEngine,
                                       InventoryGateway inventoryGateway,
                                       FraudDetectionService fraudDetectionService,
                                       LoyaltyLedger loyaltyLedger,
                                       NotificationGateway notificationGateway) {
        this.discountEngine = Objects.requireNonNull(discountEngine, "discountEngine");
        this.inventoryGateway = Objects.requireNonNull(inventoryGateway, "inventoryGateway");
        this.fraudDetectionService = Objects.requireNonNull(fraudDetectionService, "fraudDetectionService");
        this.loyaltyLedger = Objects.requireNonNull(loyaltyLedger, "loyaltyLedger");
        this.notificationGateway = Objects.requireNonNull(notificationGateway, "notificationGateway");
    }

    public CampaignReport orchestrateCampaign(Order order,
                                              CustomerProfile customer,
                                              SeasonalPromotion promotion) {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(customer, "customer");
        Objects.requireNonNull(promotion, "promotion");

        RecommendationPipeline pipeline = new RecommendationPipeline(promotion);
        RecommendationPipeline.Context context = pipeline.prepare(order, customer, LocalDate.now());
        RecommendationPipeline.Plan plan = pipeline.generatePlan(context);

        CampaignMetricsCollector metricsCollector = new CampaignMetricsCollector(order.getOrderId(), promotion.getName());
        metricsCollector.recordRecommendationPlan(plan);

        InventoryGateway.InventoryReservation reservation = inventoryGateway.reserve(order, plan);
        metricsCollector.recordReservation(reservation);

        FraudDetectionService.FraudAlert alert = fraudDetectionService.inspect(order, customer, reservation);
        if (alert.blocked()) {
            metricsCollector.recordFraud(alert);
            NotificationGateway.NotificationMessage fraudMessage = NotificationGateway.NotificationMessage.fraudulent(order.getOrderId(), alert.reason());
            NotificationGateway.NotificationReceipt fraudReceipt = notificationGateway.send(fraudMessage);
            metricsCollector.recordNotification(fraudReceipt);
            return metricsCollector.buildReport(DiscountOutcome.skipped(alert.reason()), reservation, fraudReceipt);
        }

        CampaignDiscountCalculator calculator = new CampaignDiscountCalculator(discountEngine);
        CampaignDiscountCalculator.Result result = calculator.calculate(order, customer, promotion, plan, LocalDate.now());
        DiscountOutcome outcome = result.outcome();
        metricsCollector.recordDiscount(outcome, result.result());

        LoyaltyLedger.LoyaltySnapshot loyaltySnapshot = loyaltyLedger.recordAccrual(customer, outcome);
        metricsCollector.recordLoyalty(loyaltySnapshot);

        NotificationGateway.NotificationMessage message = outcome.applied()
                ? NotificationGateway.NotificationMessage.success(order.getOrderId(), outcome.discountAmount())
                : NotificationGateway.NotificationMessage.neutral(order.getOrderId(), outcome.reason());
        NotificationGateway.NotificationReceipt receipt = notificationGateway.send(message);
        metricsCollector.recordNotification(receipt);

        return metricsCollector.buildReport(outcome, reservation, receipt);
    }
}
