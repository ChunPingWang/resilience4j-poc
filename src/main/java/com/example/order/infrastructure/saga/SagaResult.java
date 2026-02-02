package com.example.order.infrastructure.saga;

/**
 * Result of saga execution.
 */
public record SagaResult(
        String orderId,
        boolean success,
        String trackingNumber,
        boolean deferredShipping,
        String errorMessage
) {

    public static SagaResult success(String orderId, String trackingNumber) {
        return new SagaResult(orderId, true, trackingNumber, false, null);
    }

    public static SagaResult successWithDeferredShipping(String orderId) {
        return new SagaResult(orderId, true, null, true, null);
    }

    public static SagaResult failure(String orderId, String errorMessage) {
        return new SagaResult(orderId, false, null, false, errorMessage);
    }
}
