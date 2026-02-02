package com.example.order.infrastructure.adapter.in.web.mapper;

import com.example.order.application.dto.CreateOrderCommand;
import com.example.order.application.dto.CreateOrderCommand.OrderItemDto;
import com.example.order.application.dto.OrderResult;
import com.example.order.infrastructure.adapter.in.web.dto.CreateOrderRequest;
import com.example.order.infrastructure.adapter.in.web.dto.CreateOrderResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mapper between web DTOs and application DTOs.
 */
@Component
public class OrderWebMapper {

    public CreateOrderCommand toCommand(CreateOrderRequest request) {
        List<OrderItemDto> items = request.items().stream()
                .map(item -> new OrderItemDto(
                        item.skuCode(),
                        item.quantity(),
                        item.unitPrice()))
                .toList();

        return new CreateOrderCommand(items, request.shippingAddress());
    }

    public CreateOrderResponse toResponse(OrderResult result) {
        if ("FAILED".equals(result.status())) {
            return CreateOrderResponse.failure(result.orderId(), result.message());
        }

        if (result.trackingNumber() == null) {
            return CreateOrderResponse.successWithDeferredShipping(
                    result.orderId(),
                    result.status(),
                    result.totalAmount(),
                    result.currency(),
                    result.createdAt());
        }

        return CreateOrderResponse.success(
                result.orderId(),
                result.status(),
                result.totalAmount(),
                result.currency(),
                result.trackingNumber(),
                result.createdAt());
    }
}
