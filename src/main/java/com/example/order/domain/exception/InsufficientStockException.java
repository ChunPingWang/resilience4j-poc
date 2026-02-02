package com.example.order.domain.exception;

import com.example.order.domain.model.SkuCode;

/**
 * Exception thrown when there is insufficient stock for a product.
 */
public class InsufficientStockException extends DomainException {

    private final SkuCode skuCode;
    private final int requestedQuantity;
    private final int availableQuantity;

    public InsufficientStockException(SkuCode skuCode, int requestedQuantity, int availableQuantity) {
        super(String.format("Insufficient stock for SKU %s: requested %d, available %d",
                skuCode, requestedQuantity, availableQuantity));
        this.skuCode = skuCode;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }

    public SkuCode getSkuCode() {
        return skuCode;
    }

    public int getRequestedQuantity() {
        return requestedQuantity;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }
}
