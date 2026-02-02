package com.example.order.infrastructure.adapter.out.shipping.dto;

import java.util.List;

/**
 * Request DTO for shipping service.
 */
public record ShippingRequest(
        String orderId,
        String address,
        List<ShippingItemDto> items
) {
    public static ShippingRequest of(String orderId, String address, List<ShippingItemDto> items) {
        return new ShippingRequest(orderId, address, items);
    }

    public record ShippingItemDto(
            String skuCode,
            int quantity
    ) {
        public static ShippingItemDto of(String skuCode, int quantity) {
            return new ShippingItemDto(skuCode, quantity);
        }
    }
}
