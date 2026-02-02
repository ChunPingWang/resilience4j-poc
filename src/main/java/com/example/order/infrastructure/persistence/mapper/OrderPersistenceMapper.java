package com.example.order.infrastructure.persistence.mapper;

import com.example.order.domain.model.*;
import com.example.order.infrastructure.persistence.entity.OrderEntity;
import com.example.order.infrastructure.persistence.entity.OrderItemEntity;
import com.example.order.infrastructure.persistence.entity.OrderStatusEnum;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper between domain Order and persistence OrderEntity.
 */
@Component
public class OrderPersistenceMapper {

    public OrderEntity toEntity(Order order, String idempotencyKey) {
        OrderEntity entity = new OrderEntity();
        entity.setId(order.getOrderId().getValue());
        entity.setIdempotencyKey(idempotencyKey);
        entity.setShippingAddress(order.getShippingAddress());
        entity.setStatus(toStatusEnum(order.getStatus()));
        entity.setTotalAmount(order.getTotalAmount().getAmount());
        entity.setCurrency(order.getTotalAmount().getCurrency());
        entity.setCreatedAt(order.getCreatedAt());

        for (OrderItem item : order.getItems()) {
            OrderItemEntity itemEntity = new OrderItemEntity();
            itemEntity.setSkuCode(item.getSkuCode().getValue());
            itemEntity.setQuantity(item.getQuantity());
            itemEntity.setUnitPrice(item.getUnitPrice().getAmount());
            entity.addItem(itemEntity);
        }

        return entity;
    }

    public Order toDomain(OrderEntity entity) {
        List<OrderItem> items = entity.getItems().stream()
                .map(this::toOrderItem)
                .collect(Collectors.toList());

        return Order.reconstitute(
                OrderId.of(entity.getId()),
                items,
                entity.getShippingAddress(),
                entity.getCreatedAt(),
                toDomainStatus(entity.getStatus())
        );
    }

    private OrderItem toOrderItem(OrderItemEntity entity) {
        return OrderItem.of(
                SkuCode.of(entity.getSkuCode()),
                entity.getQuantity(),
                Money.of(entity.getUnitPrice())
        );
    }

    public OrderStatusEnum toStatusEnum(OrderStatus status) {
        return switch (status) {
            case PENDING -> OrderStatusEnum.PENDING;
            case INVENTORY_RESERVED -> OrderStatusEnum.INVENTORY_RESERVED;
            case PAYMENT_COMPLETED -> OrderStatusEnum.PAYMENT_COMPLETED;
            case SHIPPING_REQUESTED -> OrderStatusEnum.SHIPPING_REQUESTED;
            case COMPLETED -> OrderStatusEnum.COMPLETED;
            case FAILED -> OrderStatusEnum.FAILED;
        };
    }

    public OrderStatus toDomainStatus(OrderStatusEnum status) {
        return switch (status) {
            case PENDING, PROCESSING -> OrderStatus.PENDING;
            case INVENTORY_RESERVED -> OrderStatus.INVENTORY_RESERVED;
            case PAYMENT_COMPLETED -> OrderStatus.PAYMENT_COMPLETED;
            case SHIPPING_REQUESTED -> OrderStatus.SHIPPING_REQUESTED;
            case COMPLETED -> OrderStatus.COMPLETED;
            case FAILED -> OrderStatus.FAILED;
        };
    }
}
