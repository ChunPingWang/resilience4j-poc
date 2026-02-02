package com.example.order.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Value Object representing a unique order identifier.
 */
public final class OrderId {

    private final String value;

    private OrderId(String value) {
        this.value = Objects.requireNonNull(value, "OrderId value cannot be null");
    }

    /**
     * Creates a new OrderId with the given UUID string.
     *
     * @param value UUID string
     * @return new OrderId instance
     * @throws IllegalArgumentException if value is not a valid UUID
     */
    public static OrderId of(String value) {
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid OrderId format: " + value, e);
        }
        return new OrderId(value);
    }

    /**
     * Generates a new random OrderId.
     *
     * @return new OrderId with random UUID
     */
    public static OrderId generate() {
        return new OrderId(UUID.randomUUID().toString());
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderId orderId = (OrderId) o;
        return Objects.equals(value, orderId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
