package com.example.order.infrastructure.saga;

import com.example.order.application.port.out.InventoryPort;
import com.example.order.application.port.out.InventoryPort.InventoryReservationResult;
import com.example.order.application.port.out.PaymentPort;
import com.example.order.application.port.out.PaymentPort.PaymentResult;
import com.example.order.application.port.out.ShippingPort;
import com.example.order.application.port.out.ShippingPort.ShippingResult;
import com.example.order.application.port.out.ShippingPort.ShippingStatus;
import com.example.order.domain.model.Money;
import com.example.order.domain.model.OrderId;
import com.example.order.domain.model.OrderItem;
import com.example.order.domain.model.SkuCode;
import com.example.order.infrastructure.persistence.entity.OrderEntity;
import com.example.order.infrastructure.persistence.entity.OrderItemEntity;
import com.example.order.infrastructure.persistence.entity.OrderStatusEnum;
import com.example.order.infrastructure.persistence.repository.OrderJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Saga Orchestrator for order processing.
 * Handles the order flow: Inventory → Payment → Shipping
 * with compensation logic for failures.
 */
@Component
public class SagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrator.class);

    private final OrderJpaRepository orderRepository;
    private final InventoryPort inventoryPort;
    private final PaymentPort paymentPort;
    private final ShippingPort shippingPort;

    public SagaOrchestrator(
            OrderJpaRepository orderRepository,
            InventoryPort inventoryPort,
            PaymentPort paymentPort,
            ShippingPort shippingPort) {
        this.orderRepository = orderRepository;
        this.inventoryPort = inventoryPort;
        this.paymentPort = paymentPort;
        this.shippingPort = shippingPort;
    }

    /**
     * Executes the order processing saga.
     *
     * @param orderId the order ID to process
     * @return CompletableFuture with the processing result
     */
    public CompletableFuture<SagaResult> executeSaga(String orderId) {
        log.info("Starting saga for order: {}", orderId);

        return orderRepository.findById(orderId)
                .map(order -> executeOrderSaga(order))
                .orElseGet(() -> CompletableFuture.completedFuture(
                        SagaResult.failure(orderId, "Order not found")));
    }

    private CompletableFuture<SagaResult> executeOrderSaga(OrderEntity order) {
        String orderId = order.getId();

        // Update status to PROCESSING
        updateOrderStatus(orderId, OrderStatusEnum.PROCESSING, null);

        // Step 1: Reserve Inventory
        return reserveInventory(order)
                .thenCompose(inventoryResult -> {
                    if (!inventoryResult.reserved()) {
                        return CompletableFuture.completedFuture(
                                handleInventoryFailure(order, inventoryResult));
                    }

                    updateOrderStatus(orderId, OrderStatusEnum.INVENTORY_RESERVED, null);

                    // Step 2: Process Payment
                    return processPayment(order)
                            .thenCompose(paymentResult -> {
                                if (!paymentResult.success()) {
                                    // Compensate: Release inventory
                                    compensateInventory(order);
                                    return CompletableFuture.completedFuture(
                                            handlePaymentFailure(order, paymentResult));
                                }

                                updateOrderStatus(orderId, OrderStatusEnum.PAYMENT_COMPLETED,
                                        paymentResult.transactionId());

                                // Step 3: Create Shipment (with graceful degradation)
                                return createShipment(order)
                                        .thenApply(shippingResult ->
                                                handleShippingResult(order, shippingResult));
                            });
                })
                .exceptionally(throwable -> {
                    log.error("Saga failed for order: {}", orderId, throwable);
                    updateOrderStatus(orderId, OrderStatusEnum.FAILED,
                            throwable.getMessage());
                    return SagaResult.failure(orderId, throwable.getMessage());
                });
    }

    private CompletableFuture<InventoryReservationResult> reserveInventory(OrderEntity order) {
        log.debug("Reserving inventory for order: {}", order.getId());

        // Reserve inventory for each item
        List<CompletableFuture<InventoryReservationResult>> reservations = order.getItems().stream()
                .map(item -> inventoryPort.reserveInventory(
                        SkuCode.of(item.getSkuCode()),
                        item.getQuantity()))
                .toList();

        // Combine all reservations - fail if any fails
        return CompletableFuture.allOf(reservations.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    boolean allReserved = reservations.stream()
                            .map(CompletableFuture::join)
                            .allMatch(InventoryReservationResult::reserved);

                    if (allReserved) {
                        return InventoryReservationResult.success(
                                order.getItems().get(0).getSkuCode(), 0);
                    } else {
                        return InventoryReservationResult.failure(
                                "INSUFFICIENT_STOCK", "One or more items could not be reserved");
                    }
                })
                .exceptionally(throwable -> {
                    log.error("Inventory reservation failed", throwable);
                    Throwable cause = throwable instanceof CompletionException ?
                            throwable.getCause() : throwable;
                    return InventoryReservationResult.failure(
                            "RESERVATION_ERROR", cause.getMessage());
                });
    }

    private CompletableFuture<PaymentResult> processPayment(OrderEntity order) {
        log.debug("Processing payment for order: {}", order.getId());

        BigDecimal totalAmount = order.getTotalAmount();
        String currency = order.getCurrency() != null ? order.getCurrency() : "TWD";

        return paymentPort.processPayment(
                OrderId.of(order.getId()),
                Money.of(totalAmount, currency),
                order.getIdempotencyKey());
    }

    private CompletableFuture<ShippingResult> createShipment(OrderEntity order) {
        log.debug("Creating shipment for order: {}", order.getId());

        List<OrderItem> items = order.getItems().stream()
                .map(item -> OrderItem.of(
                        SkuCode.of(item.getSkuCode()),
                        item.getQuantity(),
                        Money.of(item.getUnitPrice())))
                .toList();

        return shippingPort.createShipment(
                OrderId.of(order.getId()),
                order.getShippingAddress(),
                items);
    }

    private void compensateInventory(OrderEntity order) {
        log.info("Compensating inventory for order: {}", order.getId());
        // In a real implementation, this would release the reserved inventory
        // For this PoC, we just log the compensation
        order.getItems().forEach(item ->
                log.info("Would release {} units of SKU {} for order {}",
                        item.getQuantity(), item.getSkuCode(), order.getId()));
    }

    private SagaResult handleInventoryFailure(OrderEntity order, InventoryReservationResult result) {
        String message = "庫存預留失敗: " + result.errorMessage();
        updateOrderStatus(order.getId(), OrderStatusEnum.FAILED, message);
        return SagaResult.failure(order.getId(), message);
    }

    private SagaResult handlePaymentFailure(OrderEntity order, PaymentResult result) {
        String message = "支付處理失敗: " + result.errorMessage();
        updateOrderStatus(order.getId(), OrderStatusEnum.FAILED, message);
        return SagaResult.failure(order.getId(), message);
    }

    private SagaResult handleShippingResult(OrderEntity order, ShippingResult result) {
        String orderId = order.getId();

        if (result.deferred()) {
            // Shipping is deferred but order is successful
            updateOrderStatusWithTracking(orderId, OrderStatusEnum.COMPLETED, null,
                    "訂單完成，物流單號稍後通知");
            return SagaResult.successWithDeferredShipping(orderId);
        } else if (result.trackingNumber() != null) {
            // Full success
            updateOrderStatusWithTracking(orderId, OrderStatusEnum.COMPLETED,
                    result.trackingNumber(), "訂單完成");
            return SagaResult.success(orderId, result.trackingNumber());
        } else {
            // Shipping failed but we still complete the order (graceful degradation)
            updateOrderStatusWithTracking(orderId, OrderStatusEnum.COMPLETED, null,
                    "訂單完成，物流建單失敗將稍後重試");
            return SagaResult.successWithDeferredShipping(orderId);
        }
    }

    @Transactional
    protected void updateOrderStatus(String orderId, OrderStatusEnum status, String errorMessage) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setStatus(status);
            if (errorMessage != null) {
                order.setErrorMessage(errorMessage);
            }
            orderRepository.save(order);
            log.debug("Updated order {} status to {}", orderId, status);
        });
    }

    @Transactional
    protected void updateOrderStatusWithTracking(String orderId, OrderStatusEnum status,
                                                  String trackingNumber, String message) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setStatus(status);
            order.setTrackingNumber(trackingNumber);
            order.setErrorMessage(message);
            orderRepository.save(order);
            log.debug("Updated order {} status to {} with tracking {}",
                    orderId, status, trackingNumber);
        });
    }
}
