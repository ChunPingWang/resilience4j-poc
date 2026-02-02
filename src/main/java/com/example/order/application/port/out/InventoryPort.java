package com.example.order.application.port.out;

import com.example.order.domain.model.SkuCode;

import java.util.concurrent.CompletableFuture;

/**
 * Outbound port for inventory service operations.
 */
public interface InventoryPort {

    /**
     * Reserves inventory for a product.
     *
     * @param skuCode  the product SKU
     * @param quantity the quantity to reserve
     * @return future containing the reservation result
     */
    CompletableFuture<InventoryReservationResult> reserveInventory(SkuCode skuCode, int quantity);

    /**
     * Result of an inventory reservation operation.
     */
    record InventoryReservationResult(
            String skuCode,
            boolean reserved,
            int remainingQuantity
    ) {
        public static InventoryReservationResult success(String skuCode, int remainingQuantity) {
            return new InventoryReservationResult(skuCode, true, remainingQuantity);
        }

        public static InventoryReservationResult failure(String skuCode, int remainingQuantity) {
            return new InventoryReservationResult(skuCode, false, remainingQuantity);
        }
    }
}
