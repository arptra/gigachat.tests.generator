package com.acme.discount.complex;

import com.acme.discount.CustomerProfile;
import com.acme.discount.DiscountEngine;
import com.acme.discount.DiscountOutcome;
import com.acme.discount.Order;
import com.acme.discount.SeasonalPromotion;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Example class with a mixture of constructor-injected collaborators,
 * ad-hoc instantiations via {@code new}, calls to static {@code void}
 * helpers and regular business logic. The generator can use it to
 * showcase how mock suggestions are produced for different code
 * patterns inside the same method.
 */
public class FlashSaleCoordinator {
    private final DiscountEngine discountEngine;
    private final InventoryGateway inventoryGateway;
    private final NotificationGateway notificationGateway;

    public FlashSaleCoordinator(DiscountEngine discountEngine,
                                InventoryGateway inventoryGateway,
                                NotificationGateway notificationGateway) {
        this.discountEngine = Objects.requireNonNull(discountEngine, "discountEngine");
        this.inventoryGateway = Objects.requireNonNull(inventoryGateway, "inventoryGateway");
        this.notificationGateway = Objects.requireNonNull(notificationGateway, "notificationGateway");
    }

    public CampaignReport launchFlashSale(Order order,
                                          CustomerProfile customer,
                                          SeasonalPromotion promotion) {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(customer, "customer");
        Objects.requireNonNull(promotion, "promotion");

        FlashSaleAuditLog.recordStart(order.getOrderId(), promotion.getName());

        RecommendationPipeline pipeline = new RecommendationPipeline(promotion);
        RecommendationPipeline.Context context = pipeline.prepare(order, customer, LocalDate.now());
        RecommendationPipeline.Plan plan = pipeline.generatePlan(context);

        CampaignMetricsCollector metricsCollector = new CampaignMetricsCollector(order.getOrderId(), promotion.getName());
        metricsCollector.recordRecommendationPlan(plan);

        InventoryGateway.InventoryReservation reservation = inventoryGateway.reserve(order, plan);
        metricsCollector.recordReservation(reservation);

        CampaignDiscountCalculator calculator = new CampaignDiscountCalculator(discountEngine);
        CampaignDiscountCalculator.Result result = calculator.calculate(order, customer, promotion, plan, LocalDate.now());
        DiscountOutcome outcome = result.outcome();
        metricsCollector.recordDiscount(outcome, result.result());

        NotificationGateway.NotificationMessage message = outcome.applied()
                ? NotificationGateway.NotificationMessage.success(order.getOrderId(), outcome.discountAmount())
                : NotificationGateway.NotificationMessage.neutral(order.getOrderId(), outcome.reason());
        NotificationGateway.NotificationReceipt receipt = notificationGateway.send(message);
        metricsCollector.recordNotification(receipt);

        FlashSaleAuditLog.recordCompletion(order.getOrderId(), outcome.applied(), receipt.channel());

        return metricsCollector.buildReport(outcome, reservation, receipt);
    }

    public boolean requiresManualReview(Order order) {
        Objects.requireNonNull(order, "order");
        return order.getLines().size() > 5 || order.getSubtotal() > 500.0;
    }

    public void cancelFlashSale(String orderId) {
        Objects.requireNonNull(orderId, "orderId");
        FlashSaleAuditLog.recordCancellation(orderId);
    }
}
