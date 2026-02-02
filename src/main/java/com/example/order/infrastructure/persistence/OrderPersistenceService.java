package com.example.order.infrastructure.persistence;

import com.example.order.application.dto.CreateOrderCommand;
import com.example.order.application.dto.OrderResult;
import com.example.order.domain.model.Money;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderItem;
import com.example.order.domain.model.OrderStatus;
import com.example.order.domain.model.SkuCode;
import com.example.order.infrastructure.persistence.entity.*;
import com.example.order.infrastructure.persistence.mapper.OrderPersistenceMapper;
import com.example.order.infrastructure.persistence.repository.OrderJpaRepository;
import com.example.order.infrastructure.persistence.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for order persistence with Outbox pattern.
 * Ensures order and outbox event are persisted in a single transaction.
 */
@Service
public class OrderPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(OrderPersistenceService.class);

    private final OrderJpaRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final OrderPersistenceMapper mapper;
    private final ObjectMapper objectMapper;

    public OrderPersistenceService(
            OrderJpaRepository orderRepository,
            OutboxRepository outboxRepository,
            OrderPersistenceMapper mapper,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates an order and publishes an OrderCreated event to the outbox.
     * Both operations are performed in a single transaction.
     *
     * @param command        the create order command
     * @param idempotencyKey the idempotency key
     * @return OrderResult indicating the order was accepted for processing
     */
    @Transactional
    public OrderResult createOrderWithOutbox(CreateOrderCommand command, String idempotencyKey) {
        log.debug("Creating order with outbox pattern, idempotencyKey: {}", idempotencyKey);

        // Create domain order
        List<OrderItem> items = command.items().stream()
                .map(item -> OrderItem.of(
                        SkuCode.of(item.skuCode()),
                        item.quantity(),
                        Money.of(item.unitPrice())))
                .toList();

        Order order = Order.create(items, command.shippingAddress());
        String orderId = order.getOrderId().getValue();

        // Create and save order entity
        OrderEntity orderEntity = mapper.toEntity(order, idempotencyKey);
        orderEntity.setStatus(OrderStatusEnum.PENDING);

        // Calculate and set total amount
        BigDecimal totalAmount = order.getTotalAmount().getAmount();
        orderEntity.setTotalAmount(totalAmount);
        orderEntity.setCurrency("TWD");

        orderRepository.save(orderEntity);
        log.debug("Saved order entity: {}", orderId);

        // Create outbox event in the same transaction
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setId(UUID.randomUUID().toString());
        outboxEvent.setAggregateType("Order");
        outboxEvent.setAggregateId(orderId);
        outboxEvent.setEventType("OrderCreated");
        outboxEvent.setPayload(serializeOrderPayload(orderEntity));
        outboxEvent.setStatus(OutboxEventStatus.PENDING);

        outboxRepository.save(outboxEvent);
        log.debug("Saved outbox event for order: {}", orderId);

        // Return immediately with PENDING status
        // The actual processing will be done by the OutboxPoller
        return OrderResult.pending(
                orderId,
                totalAmount,
                "TWD",
                order.getCreatedAt(),
                "訂單已接收，正在處理中"
        );
    }

    /**
     * Retrieves the current status of an order.
     *
     * @param orderId the order ID
     * @return Optional containing the order result if found
     */
    @Transactional(readOnly = true)
    public Optional<OrderResult> getOrderResult(String orderId) {
        return orderRepository.findById(orderId)
                .map(this::toOrderResult);
    }

    /**
     * Retrieves an order by its idempotency key.
     *
     * @param idempotencyKey the idempotency key
     * @return Optional containing the order result if found
     */
    @Transactional(readOnly = true)
    public Optional<OrderResult> getOrderByIdempotencyKey(String idempotencyKey) {
        return orderRepository.findByIdempotencyKey(idempotencyKey)
                .map(this::toOrderResult);
    }

    private OrderResult toOrderResult(OrderEntity entity) {
        String status = mapStatus(entity.getStatus());

        if (entity.getStatus() == OrderStatusEnum.FAILED) {
            return OrderResult.failure(
                    entity.getId(),
                    entity.getErrorMessage() != null ? entity.getErrorMessage() : "Order processing failed"
            );
        }

        if (entity.getStatus() == OrderStatusEnum.COMPLETED) {
            if (entity.getTrackingNumber() != null) {
                return OrderResult.success(
                        entity.getId(),
                        entity.getTotalAmount(),
                        entity.getCurrency(),
                        entity.getTrackingNumber(),
                        entity.getCreatedAt()
                );
            } else {
                return OrderResult.successWithDeferredShipping(
                        entity.getId(),
                        entity.getTotalAmount(),
                        entity.getCurrency(),
                        entity.getCreatedAt()
                );
            }
        }

        // For PENDING, PROCESSING, etc.
        return OrderResult.pending(
                entity.getId(),
                entity.getTotalAmount(),
                entity.getCurrency(),
                entity.getCreatedAt(),
                "訂單處理中: " + status
        );
    }

    private String mapStatus(OrderStatusEnum status) {
        return switch (status) {
            case PENDING -> "PENDING";
            case PROCESSING -> "PROCESSING";
            case INVENTORY_RESERVED -> "INVENTORY_RESERVED";
            case PAYMENT_COMPLETED -> "PAYMENT_COMPLETED";
            case SHIPPING_REQUESTED -> "SHIPPING_REQUESTED";
            case COMPLETED -> "COMPLETED";
            case FAILED -> "FAILED";
        };
    }

    private String serializeOrderPayload(OrderEntity entity) {
        try {
            return objectMapper.writeValueAsString(new OrderPayload(
                    entity.getId(),
                    entity.getIdempotencyKey(),
                    entity.getShippingAddress(),
                    entity.getTotalAmount(),
                    entity.getCurrency()
            ));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize order payload", e);
            return "{}";
        }
    }

    private record OrderPayload(
            String orderId,
            String idempotencyKey,
            String shippingAddress,
            BigDecimal totalAmount,
            String currency
    ) {}
}
