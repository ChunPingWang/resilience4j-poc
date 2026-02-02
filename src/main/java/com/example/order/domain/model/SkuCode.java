package com.example.order.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object representing a product SKU code.
 * Format: 3 uppercase letters followed by 3 digits (e.g., SKU001, PRD123)
 */
public final class SkuCode {

    private static final Pattern SKU_PATTERN = Pattern.compile("^[A-Z]{3}[0-9]{3}$");

    private final String value;

    private SkuCode(String value) {
        this.value = Objects.requireNonNull(value, "SkuCode value cannot be null");
    }

    /**
     * Creates a new SkuCode with the given value.
     *
     * @param value SKU code string
     * @return new SkuCode instance
     * @throws IllegalArgumentException if value doesn't match pattern [A-Z]{3}[0-9]{3}
     */
    public static SkuCode of(String value) {
        if (value == null || !SKU_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Invalid SkuCode format: " + value + ". Expected pattern: [A-Z]{3}[0-9]{3}");
        }
        return new SkuCode(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SkuCode skuCode = (SkuCode) o;
        return Objects.equals(value, skuCode.value);
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
