package com.example.order.application.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Command for creating a new order.
 */
public record CreateOrderCommand(
        List<OrderItemDto> items,
        String shippingAddress
) {
    public CreateOrderCommand {
        Objects.requireNonNull(items, "Items cannot be null");
        Objects.requireNonNull(shippingAddress, "ShippingAddress cannot be null");
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Items cannot be empty");
        }
        if (shippingAddress.isBlank()) {
            throw new IllegalArgumentException("ShippingAddress cannot be blank");
        }
    }

    /**
     * DTO for order item in the command.
     */
    public record OrderItemDto(
            String skuCode,
            int quantity,
            BigDecimal unitPrice
    ) {
        public OrderItemDto {
            Objects.requireNonNull(skuCode, "SkuCode cannot be null");
            Objects.requireNonNull(unitPrice, "UnitPrice cannot be null");
            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
            }
            if (unitPrice.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("UnitPrice cannot be negative");
            }
        }
    }
}
