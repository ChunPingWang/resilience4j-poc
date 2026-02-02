package com.example.order.application.port.in;

import com.example.order.application.dto.CreateOrderCommand;
import com.example.order.application.dto.OrderResult;

import java.util.concurrent.CompletableFuture;

/**
 * Inbound port for creating orders.
 */
public interface CreateOrderUseCase {

    /**
     * Creates a new order with the given command.
     *
     * @param command the order creation command
     * @return future containing the order result
     */
    CompletableFuture<OrderResult> createOrder(CreateOrderCommand command);
}
