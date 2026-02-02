package com.example.order.infrastructure.adapter.out.payment.dto;

/**
 * Response DTO from payment service.
 */
public record PaymentResponse(
        String transactionId,
        String status,
        String message
) {
}
