package com.acme.discount.example;

import com.acme.discount.Order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class InventoryGateway {

    public InventoryReservation reserve(Order order, RecommendationPipeline.Plan plan) {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(plan, "plan");
        List<String> reservedSkus = new ArrayList<>();
        for (String category : plan.targetCategories()) {
            for (int index = 0; index < order.getLines().size(); index++) {
                reservedSkus.add(category + "-" + index);
            }
        }
        return new InventoryReservation(order.getOrderId(), reservedSkus, Instant.now());
    }

    public record InventoryReservation(String orderId, List<String> reservedSkus, Instant createdAt) {
        public InventoryReservation {
            Objects.requireNonNull(orderId, "orderId");
            reservedSkus = List.copyOf(reservedSkus);
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }
}
