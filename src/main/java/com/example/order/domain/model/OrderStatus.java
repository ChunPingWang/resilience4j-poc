package com.example.order.domain.model;

/**
 * Enum representing the possible states of an Order.
 */
public enum OrderStatus {

    /**
     * Initial state, awaiting processing.
     */
    PENDING,

    /**
     * Inventory has been successfully reserved.
     */
    INVENTORY_RESERVED,

    /**
     * Payment has been successfully completed.
     */
    PAYMENT_COMPLETED,

    /**
     * Shipping request has been submitted (may be deferred).
     */
    SHIPPING_REQUESTED,

    /**
     * Order has been successfully completed.
     */
    COMPLETED,

    /**
     * Order has failed due to an unrecoverable error.
     */
    FAILED
}
