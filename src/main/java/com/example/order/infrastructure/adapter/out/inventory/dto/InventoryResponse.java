package com.example.order.infrastructure.adapter.out.inventory.dto;

/**
 * Response DTO from inventory service.
 */
public record InventoryResponse(
        String skuCode,
        boolean reserved,
        int remainingQty
) {
}
