package com.example.order.infrastructure.adapter.out.shipping.dto;

/**
 * Response DTO from shipping service.
 */
public record ShippingResponse(
        String trackingNumber,
        String status,
        String message
) {
}
