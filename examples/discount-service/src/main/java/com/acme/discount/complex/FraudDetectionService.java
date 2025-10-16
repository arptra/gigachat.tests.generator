package com.acme.discount.complex;

import com.acme.discount.CustomerProfile;
import com.acme.discount.Order;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class FraudDetectionService {

    public FraudAlert inspect(Order order,
                              CustomerProfile customer,
                              InventoryGateway.InventoryReservation reservation) {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(customer, "customer");
        Objects.requireNonNull(reservation, "reservation");

        boolean suspicious = order.getSubtotal() > 1000
                && Duration.between(reservation.createdAt(), Instant.now()).toSeconds() < 1
                && customer.getLoyaltyTier().name().contains("NEW");
        if (suspicious) {
            return new FraudAlert(true, "High value order too fast");
        }
        return new FraudAlert(false, "No anomalies detected");
    }

    public record FraudAlert(boolean blocked, String reason) {
        public FraudAlert {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
