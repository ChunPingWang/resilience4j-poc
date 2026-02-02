package com.example.order.infrastructure.adapter.out.payment.dto;

import java.math.BigDecimal;

/**
 * Request DTO for payment service.
 */
public record PaymentRequest(
        String orderId,
        BigDecimal amount,
        String currency
) {
    public static PaymentRequest of(String orderId, BigDecimal amount, String currency) {
        return new PaymentRequest(orderId, amount, currency);
    }
}
