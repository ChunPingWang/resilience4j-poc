package com.example.order.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Result of order creation.
 */
public record OrderResult(
        String orderId,
        String status,
        BigDecimal totalAmount,
        String currency,
        String trackingNumber,
        String message,
        Instant createdAt
) {
    /**
     * Creates a successful order result with all details.
     */
    public static OrderResult success(String orderId, String status, BigDecimal totalAmount,
                                       String currency, String trackingNumber, Instant createdAt) {
        return new OrderResult(orderId, status, totalAmount, currency, trackingNumber,
                "Order created successfully", createdAt);
    }

    /**
     * Creates a successful order result (simplified version).
     */
    public static OrderResult success(String orderId, BigDecimal totalAmount, String currency,
                                       String trackingNumber, Instant createdAt) {
        return new OrderResult(orderId, "COMPLETED", totalAmount, currency, trackingNumber,
                "Order created successfully", createdAt);
    }

    /**
     * Creates a successful order result with deferred shipping.
     */
    public static OrderResult successWithDeferredShipping(String orderId, String status,
                                                           BigDecimal totalAmount, String currency,
                                                           Instant createdAt) {
        return new OrderResult(orderId, status, totalAmount, currency, null,
                "Order created. Tracking number will be provided later via notification.", createdAt);
    }

    /**
     * Creates a successful order result with deferred shipping (simplified version).
     */
    public static OrderResult successWithDeferredShipping(String orderId, BigDecimal totalAmount,
                                                           String currency, Instant createdAt) {
        return new OrderResult(orderId, "COMPLETED", totalAmount, currency, null,
                "Order created. Tracking number will be provided later via notification.", createdAt);
    }

    /**
     * Creates a pending order result (for async processing).
     */
    public static OrderResult pending(String orderId, BigDecimal totalAmount, String currency,
                                       Instant createdAt, String message) {
        return new OrderResult(orderId, "PENDING", totalAmount, currency, null, message, createdAt);
    }

    /**
     * Creates a failed order result.
     */
    public static OrderResult failure(String orderId, String message) {
        return new OrderResult(orderId, "FAILED", null, null, null, message, null);
    }
}
