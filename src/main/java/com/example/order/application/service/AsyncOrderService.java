package com.example.order.application.service;

import com.example.order.application.dto.CreateOrderCommand;
import com.example.order.application.dto.OrderResult;
import com.example.order.application.port.in.CreateOrderUseCase;
import com.example.order.infrastructure.persistence.OrderPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Async implementation of CreateOrderUseCase using Outbox Pattern.
 * This implementation persists the order and outbox event in a single transaction,
 * then returns immediately. The actual processing is done asynchronously by the OutboxPoller.
 *
 * Activated when outbox.enabled=true
 */
@Service
@Primary
@ConditionalOnProperty(value = "outbox.enabled", havingValue = "true", matchIfMissing = false)
public class AsyncOrderService implements CreateOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(AsyncOrderService.class);

    private final OrderPersistenceService persistenceService;

    public AsyncOrderService(OrderPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Override
    public CompletableFuture<OrderResult> createOrder(CreateOrderCommand command) {
        log.info("Creating order asynchronously with outbox pattern");

        // Generate idempotency key from command or use a new one
        String idempotencyKey = generateIdempotencyKey(command);

        try {
            // This persists order + outbox event in single transaction
            OrderResult result = persistenceService.createOrderWithOutbox(command, idempotencyKey);
            log.info("Order accepted for processing: {}", result.orderId());
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("Failed to create order", e);
            return CompletableFuture.completedFuture(
                    OrderResult.failure(null, "Failed to create order: " + e.getMessage())
            );
        }
    }

    private String generateIdempotencyKey(CreateOrderCommand command) {
        // In production, this might come from the request header
        return java.util.UUID.randomUUID().toString();
    }
}
