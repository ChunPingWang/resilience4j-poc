package com.example.order.application.port.out;

import com.example.order.domain.model.Money;
import com.example.order.domain.model.OrderId;

import java.util.concurrent.CompletableFuture;

/**
 * Outbound port for payment service operations.
 */
public interface PaymentPort {

    /**
     * Processes a payment for an order.
     *
     * @param orderId        the order ID
     * @param amount         the payment amount
     * @param idempotencyKey unique key to prevent duplicate charges
     * @return future containing the payment result
     */
    CompletableFuture<PaymentResult> processPayment(OrderId orderId, Money amount, String idempotencyKey);

    /**
     * Result of a payment operation.
     */
    record PaymentResult(
            String transactionId,
            PaymentStatus status,
            String message,
            String errorMessage
    ) {
        public static PaymentResult success(String transactionId, String message) {
            return new PaymentResult(transactionId, PaymentStatus.SUCCESS, message, null);
        }

        public static PaymentResult failure(String transactionId, String message) {
            return new PaymentResult(transactionId, PaymentStatus.FAILED, message, message);
        }

        public boolean success() {
            return status == PaymentStatus.SUCCESS;
        }

        public String errorMessage() {
            return errorMessage != null ? errorMessage : message;
        }
    }

    enum PaymentStatus {
        SUCCESS,
        FAILED
    }
}
