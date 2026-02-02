package com.example.order.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate Root representing a customer order.
 */
public final class Order {

    private final OrderId orderId;
    private final List<OrderItem> items;
    private final String shippingAddress;
    private final String paymentIdempotencyKey;
    private final Instant createdAt;
    private OrderStatus status;

    private Order(OrderId orderId, List<OrderItem> items, String shippingAddress,
                  String paymentIdempotencyKey, Instant createdAt, OrderStatus status) {
        this.orderId = Objects.requireNonNull(orderId, "OrderId cannot be null");
        this.items = new ArrayList<>(Objects.requireNonNull(items, "Items cannot be null"));
        this.shippingAddress = Objects.requireNonNull(shippingAddress, "ShippingAddress cannot be null");
        this.paymentIdempotencyKey = Objects.requireNonNull(paymentIdempotencyKey, "PaymentIdempotencyKey cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "CreatedAt cannot be null");
        this.status = Objects.requireNonNull(status, "Status cannot be null");

        if (items.isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one item");
        }
    }

    /**
     * Creates a new Order with the given items and shipping address.
     *
     * @param items           the order items (must not be empty)
     * @param shippingAddress the shipping address
     * @return new Order instance
     */
    public static Order create(List<OrderItem> items, String shippingAddress) {
        return new Order(
                OrderId.generate(),
                items,
                shippingAddress,
                UUID.randomUUID().toString(),
                Instant.now(),
                OrderStatus.PENDING
        );
    }

    /**
     * Reconstitutes an Order from persistence.
     *
     * @param orderId         the order ID
     * @param items           the order items
     * @param shippingAddress the shipping address
     * @param createdAt       the creation timestamp
     * @param status          the order status
     * @return reconstituted Order instance
     */
    public static Order reconstitute(OrderId orderId, List<OrderItem> items,
                                     String shippingAddress, Instant createdAt, OrderStatus status) {
        return new Order(
                orderId,
                items,
                shippingAddress,
                UUID.randomUUID().toString(), // Will be overwritten if needed
                createdAt,
                status
        );
    }

    /**
     * Calculates the total amount for this order.
     *
     * @return the total amount as Money
     */
    public Money getTotalAmount() {
        return items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(Money.zero(), Money::add);
    }

    /**
     * Marks inventory as reserved.
     *
     * @throws IllegalStateException if order is not in PENDING status
     */
    public void markInventoryReserved() {
        validateStatusTransition(OrderStatus.PENDING, OrderStatus.INVENTORY_RESERVED);
        this.status = OrderStatus.INVENTORY_RESERVED;
    }

    /**
     * Marks payment as completed.
     *
     * @throws IllegalStateException if order is not in INVENTORY_RESERVED status
     */
    public void markPaymentCompleted() {
        validateStatusTransition(OrderStatus.INVENTORY_RESERVED, OrderStatus.PAYMENT_COMPLETED);
        this.status = OrderStatus.PAYMENT_COMPLETED;
    }

    /**
     * Marks shipping as requested.
     *
     * @throws IllegalStateException if order is not in PAYMENT_COMPLETED status
     */
    public void markShippingRequested() {
        validateStatusTransition(OrderStatus.PAYMENT_COMPLETED, OrderStatus.SHIPPING_REQUESTED);
        this.status = OrderStatus.SHIPPING_REQUESTED;
    }

    /**
     * Marks the order as completed.
     *
     * @throws IllegalStateException if order is not in SHIPPING_REQUESTED status
     */
    public void markCompleted() {
        validateStatusTransition(OrderStatus.SHIPPING_REQUESTED, OrderStatus.COMPLETED);
        this.status = OrderStatus.COMPLETED;
    }

    /**
     * Marks the order as failed.
     */
    public void markFailed() {
        if (this.status == OrderStatus.COMPLETED) {
            throw new IllegalStateException("Cannot fail a completed order");
        }
        this.status = OrderStatus.FAILED;
    }

    private void validateStatusTransition(OrderStatus expectedCurrent, OrderStatus newStatus) {
        if (this.status != expectedCurrent) {
            throw new IllegalStateException(
                    "Cannot transition from " + this.status + " to " + newStatus +
                            ". Expected current status: " + expectedCurrent);
        }
    }

    public OrderId getOrderId() {
        return orderId;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public String getPaymentIdempotencyKey() {
        return paymentIdempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public OrderStatus getStatus() {
        return status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return Objects.equals(orderId, order.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId);
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderId=" + orderId +
                ", status=" + status +
                ", itemCount=" + items.size() +
                ", totalAmount=" + getTotalAmount() +
                '}';
    }
}
