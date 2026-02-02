package com.example.order.domain.model;

import java.util.Objects;

/**
 * Entity representing a single item in an order.
 */
public final class OrderItem {

    private final SkuCode skuCode;
    private final int quantity;
    private final Money unitPrice;

    private OrderItem(SkuCode skuCode, int quantity, Money unitPrice) {
        this.skuCode = Objects.requireNonNull(skuCode, "SkuCode cannot be null");
        this.unitPrice = Objects.requireNonNull(unitPrice, "UnitPrice cannot be null");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive: " + quantity);
        }
        this.quantity = quantity;
    }

    /**
     * Creates a new OrderItem.
     *
     * @param skuCode   the product SKU code
     * @param quantity  the quantity (must be positive)
     * @param unitPrice the unit price
     * @return new OrderItem instance
     */
    public static OrderItem of(SkuCode skuCode, int quantity, Money unitPrice) {
        return new OrderItem(skuCode, quantity, unitPrice);
    }

    /**
     * Calculates the subtotal for this item (quantity * unitPrice).
     *
     * @return the subtotal as Money
     */
    public Money getSubtotal() {
        return unitPrice.multiply(quantity);
    }

    public SkuCode getSkuCode() {
        return skuCode;
    }

    public int getQuantity() {
        return quantity;
    }

    public Money getUnitPrice() {
        return unitPrice;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderItem orderItem = (OrderItem) o;
        return quantity == orderItem.quantity &&
                Objects.equals(skuCode, orderItem.skuCode) &&
                Objects.equals(unitPrice, orderItem.unitPrice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(skuCode, quantity, unitPrice);
    }

    @Override
    public String toString() {
        return "OrderItem{" +
                "skuCode=" + skuCode +
                ", quantity=" + quantity +
                ", unitPrice=" + unitPrice +
                '}';
    }
}
