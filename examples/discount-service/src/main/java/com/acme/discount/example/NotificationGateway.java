package com.acme.discount.example;

import java.time.Instant;
import java.util.Objects;

public class NotificationGateway {

    public NotificationReceipt send(NotificationMessage message) {
        Objects.requireNonNull(message, "message");
        String channel = switch (message.type()) {
            case "SUCCESS" -> "email";
            case "FRAUD" -> "sms";
            default -> "in-app";
        };
        return new NotificationReceipt(channel, Instant.now(), message);
    }

    public record NotificationMessage(String type, String orderId, String details) {
        public static NotificationMessage success(String orderId, double discount) {
            return new NotificationMessage("SUCCESS", orderId, "Discount applied: " + discount);
        }

        public static NotificationMessage fraudulent(String orderId, String reason) {
            return new NotificationMessage("FRAUD", orderId, reason);
        }

        public static NotificationMessage neutral(String orderId, String reason) {
            return new NotificationMessage("INFO", orderId, reason);
        }
    }

    public record NotificationReceipt(String channel, Instant sentAt, NotificationMessage message) {
        public NotificationReceipt {
            Objects.requireNonNull(channel, "channel");
            Objects.requireNonNull(sentAt, "sentAt");
            Objects.requireNonNull(message, "message");
        }
    }
}
