package com.example.order.infrastructure.persistence.entity;

/**
 * Order status enum for persistence layer.
 */
public enum OrderStatusEnum {
    PENDING,
    PROCESSING,
    INVENTORY_RESERVED,
    PAYMENT_COMPLETED,
    SHIPPING_REQUESTED,
    COMPLETED,
    FAILED
}
