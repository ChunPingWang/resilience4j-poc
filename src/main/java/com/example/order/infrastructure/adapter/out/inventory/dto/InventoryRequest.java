package com.example.order.infrastructure.adapter.out.inventory.dto;

/**
 * Request DTO for inventory service.
 */
public record InventoryRequest(
        String skuCode,
        int quantity
) {
    public static InventoryRequest of(String skuCode, int quantity) {
        return new InventoryRequest(skuCode, quantity);
    }
}
