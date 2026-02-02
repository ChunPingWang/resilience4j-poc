package com.example.order.application.service;

import com.example.order.application.dto.CreateOrderCommand;
import com.example.order.application.dto.CreateOrderCommand.OrderItemDto;
import com.example.order.application.dto.OrderResult;
import com.example.order.application.port.in.CreateOrderUseCase;
import com.example.order.application.port.out.InventoryPort;
import com.example.order.application.port.out.InventoryPort.InventoryReservationResult;
import com.example.order.application.port.out.PaymentPort;
import com.example.order.application.port.out.PaymentPort.PaymentResult;
import com.example.order.application.port.out.PaymentPort.PaymentStatus;
import com.example.order.application.port.out.ShippingPort;
import com.example.order.application.port.out.ShippingPort.ShippingResult;
import com.example.order.application.port.out.ShippingPort.ShippingStatus;
import com.example.order.domain.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Application service that orchestrates order creation.
 * Coordinates between domain logic and infrastructure ports.
 */
@Service
public class OrderService implements CreateOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final InventoryPort inventoryPort;
    private final PaymentPort paymentPort;
    private final ShippingPort shippingPort;

    public OrderService(
            InventoryPort inventoryPort,
            PaymentPort paymentPort,
            ShippingPort shippingPort) {
        this.inventoryPort = inventoryPort;
        this.paymentPort = paymentPort;
        this.shippingPort = shippingPort;
    }

    @Override
    public CompletableFuture<OrderResult> createOrder(CreateOrderCommand command) {
        log.info("Creating order with {} items", command.items().size());

        // Convert command to domain objects
        List<OrderItem> orderItems = command.items().stream()
                .map(this::toOrderItem)
                .toList();

        // Create order aggregate
        Order order = Order.create(orderItems, command.shippingAddress());
        log.debug("Order created: {}", order.getOrderId());

        // Execute order flow: Inventory → Payment → Shipping
        return reserveInventory(order)
                .thenCompose(o -> processPayment(o))
                .thenCompose(o -> createShipment(o))
                .thenApply(this::buildSuccessResult)
                .exceptionally(throwable -> buildFailureResult(order, throwable));
    }

    private CompletableFuture<Order> reserveInventory(Order order) {
        log.debug("Reserving inventory for order: {}", order.getOrderId());

        // Reserve inventory for each item
        List<CompletableFuture<InventoryReservationResult>> reservations = new ArrayList<>();

        for (OrderItem item : order.getItems()) {
            CompletableFuture<InventoryReservationResult> reservation = inventoryPort
                    .reserveInventory(item.getSkuCode(), item.getQuantity());
            reservations.add(reservation);
        }

        // Wait for all reservations to complete
        return CompletableFuture.allOf(reservations.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    // Verify all reservations succeeded
                    for (CompletableFuture<InventoryReservationResult> future : reservations) {
                        InventoryReservationResult result = future.join();
                        if (!result.reserved()) {
                            throw new RuntimeException("Failed to reserve inventory for SKU: " + result.skuCode());
                        }
                    }
                    order.markInventoryReserved();
                    log.info("Inventory reserved for order: {}", order.getOrderId());
                    return order;
                });
    }

    private CompletableFuture<Order> processPayment(Order order) {
        log.debug("Processing payment for order: {}", order.getOrderId());

        return paymentPort.processPayment(
                order.getOrderId(),
                order.getTotalAmount(),
                order.getPaymentIdempotencyKey()
        ).thenApply(result -> {
            if (result.status() != PaymentStatus.SUCCESS) {
                throw new RuntimeException("Payment failed: " + result.message());
            }
            order.markPaymentCompleted();
            log.info("Payment completed for order: {}, transactionId: {}",
                    order.getOrderId(), result.transactionId());
            return order;
        });
    }

    private CompletableFuture<OrderWithShipping> createShipment(Order order) {
        log.debug("Creating shipment for order: {}", order.getOrderId());

        return shippingPort.createShipment(
                order.getOrderId(),
                order.getShippingAddress(),
                order.getItems()
        ).thenApply(result -> {
            order.markShippingRequested();
            if (result.status() == ShippingStatus.CREATED) {
                order.markCompleted();
                log.info("Shipment created for order: {}, trackingNumber: {}",
                        order.getOrderId(), result.trackingNumber());
            } else {
                // Deferred shipping - still mark as completed but without tracking
                order.markCompleted();
                log.info("Shipment deferred for order: {}", order.getOrderId());
            }
            return new OrderWithShipping(order, result);
        });
    }

    private OrderResult buildSuccessResult(OrderWithShipping orderWithShipping) {
        Order order = orderWithShipping.order();
        ShippingResult shipping = orderWithShipping.shippingResult();
        Money total = order.getTotalAmount();

        if (shipping.status() == ShippingStatus.DEFERRED) {
            return OrderResult.successWithDeferredShipping(
                    order.getOrderId().getValue(),
                    order.getStatus().name(),
                    total.getAmount(),
                    total.getCurrency(),
                    order.getCreatedAt()
            );
        }

        return OrderResult.success(
                order.getOrderId().getValue(),
                order.getStatus().name(),
                total.getAmount(),
                total.getCurrency(),
                shipping.trackingNumber(),
                order.getCreatedAt()
        );
    }

    private OrderResult buildFailureResult(Order order, Throwable throwable) {
        log.error("Order creation failed for {}: {}", order.getOrderId(), throwable.getMessage());
        order.markFailed();

        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
        return OrderResult.failure(
                order.getOrderId().getValue(),
                cause.getMessage()
        );
    }

    private OrderItem toOrderItem(OrderItemDto dto) {
        return OrderItem.of(
                SkuCode.of(dto.skuCode()),
                dto.quantity(),
                Money.of(dto.unitPrice())
        );
    }

    private record OrderWithShipping(Order order, ShippingResult shippingResult) {}
}
