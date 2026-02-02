package com.example.order.infrastructure.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request DTO for creating an order via REST API.
 */
public record CreateOrderRequest(
        @NotEmpty(message = "Items cannot be empty")
        @Valid
        List<OrderItemRequest> items,

        @NotBlank(message = "Shipping address is required")
        String shippingAddress
) {
    public record OrderItemRequest(
            @NotBlank(message = "SKU code is required")
            @Pattern(regexp = "^[A-Z]{3}[0-9]{3}$", message = "Invalid SKU format")
            String skuCode,

            @Positive(message = "Quantity must be positive")
            int quantity,

            @Positive(message = "Unit price must be positive")
            BigDecimal unitPrice
    ) {}
}
