package com.example.order.infrastructure.adapter.in.web;

import com.example.order.application.dto.CreateOrderCommand;
import com.example.order.application.dto.OrderResult;
import com.example.order.application.port.in.CreateOrderUseCase;
import com.example.order.infrastructure.adapter.in.web.dto.CreateOrderRequest;
import com.example.order.infrastructure.adapter.in.web.dto.CreateOrderResponse;
import com.example.order.infrastructure.adapter.in.web.mapper.OrderWebMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

/**
 * REST controller for order operations.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final CreateOrderUseCase createOrderUseCase;
    private final OrderWebMapper mapper;

    public OrderController(CreateOrderUseCase createOrderUseCase, OrderWebMapper mapper) {
        this.createOrderUseCase = createOrderUseCase;
        this.mapper = mapper;
    }

    /**
     * Creates a new order.
     *
     * @param request the order creation request
     * @return the created order response
     */
    @PostMapping
    public CompletableFuture<ResponseEntity<CreateOrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {

        log.info("Received order request with {} items", request.items().size());

        CreateOrderCommand command = mapper.toCommand(request);

        return createOrderUseCase.createOrder(command)
                .thenApply(result -> {
                    CreateOrderResponse response = mapper.toResponse(result);

                    if ("FAILED".equals(result.status())) {
                        log.warn("Order creation failed: {}", result.message());
                        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
                    }

                    log.info("Order created successfully: {}", result.orderId());
                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                });
    }
}
