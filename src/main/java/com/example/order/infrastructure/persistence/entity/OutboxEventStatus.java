package com.example.order.infrastructure.persistence.entity;

/**
 * Status of outbox events.
 */
public enum OutboxEventStatus {
    PENDING,
    PROCESSING,
    PROCESSED,
    FAILED
}
