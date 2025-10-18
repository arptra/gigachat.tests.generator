package com.acme.discount.complex;

import java.util.Objects;

final class FlashSaleAuditLog {
    private FlashSaleAuditLog() {
    }

    static void recordStart(String orderId, String promotionName) {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(promotionName, "promotionName");
    }

    static void recordCompletion(String orderId, boolean applied, String channel) {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(channel, "channel");
    }

    static void recordCancellation(String orderId) {
        Objects.requireNonNull(orderId, "orderId");
    }
}
