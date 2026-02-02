package com.example.order.application.port.out;

import com.example.order.domain.model.OrderId;
import com.example.order.domain.model.OrderItem;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Outbound port for shipping service operations.
 */
public interface ShippingPort {

    /**
     * Creates a shipment for an order.
     *
     * @param orderId the order ID
     * @param address the shipping address
     * @param items   the items to ship
     * @return future containing the shipping result
     */
    CompletableFuture<ShippingResult> createShipment(OrderId orderId, String address, List<OrderItem> items);

    /**
     * Result of a shipping operation.
     */
    record ShippingResult(
            String trackingNumber,
            ShippingStatus status,
            String message
    ) {
        public static ShippingResult created(String trackingNumber, String message) {
            return new ShippingResult(trackingNumber, ShippingStatus.CREATED, message);
        }

        public static ShippingResult deferred(String message) {
            return new ShippingResult(null, ShippingStatus.DEFERRED, message);
        }
    }

    enum ShippingStatus {
        CREATED,
        DEFERRED
    }
}
