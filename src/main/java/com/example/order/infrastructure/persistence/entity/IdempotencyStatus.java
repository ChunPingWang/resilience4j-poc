package com.example.order.infrastructure.persistence.entity;

/**
 * Status of idempotency record processing.
 */
public enum IdempotencyStatus {
    IN_PROGRESS,  // Request is being processed
    COMPLETED,    // Request completed successfully
    FAILED        // Request failed
}
