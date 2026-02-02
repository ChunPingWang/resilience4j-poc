package com.example.order.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO for order creation.
 */
public record CreateOrderResponse(
        String orderId,
        String status,
        BigDecimal totalAmount,
        String currency,
        String trackingNumber,
        String message,
        Instant createdAt
) {
    public static CreateOrderResponse success(
            String orderId, String status, BigDecimal totalAmount,
            String currency, String trackingNumber, Instant createdAt) {
        return new CreateOrderResponse(
                orderId, status, totalAmount, currency, trackingNumber,
                "Order created successfully", createdAt);
    }

    public static CreateOrderResponse successWithDeferredShipping(
            String orderId, String status, BigDecimal totalAmount,
            String currency, Instant createdAt) {
        return new CreateOrderResponse(
                orderId, status, totalAmount, currency, null,
                "Order created. Tracking number will be provided later via notification.",
                createdAt);
    }

    public static CreateOrderResponse failure(String orderId, String message) {
        return new CreateOrderResponse(
                orderId, "FAILED", null, null, null, message, null);
    }

    public static CreateOrderResponse error(String message) {
        return new CreateOrderResponse(
                null, "ERROR", null, null, null, message, null);
    }
}
