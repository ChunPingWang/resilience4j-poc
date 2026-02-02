package com.example.order.infrastructure.adapter.out.shipping.mapper;

import com.example.order.application.port.out.ShippingPort.ShippingResult;
import com.example.order.application.port.out.ShippingPort.ShippingStatus;
import com.example.order.domain.model.OrderId;
import com.example.order.domain.model.OrderItem;
import com.example.order.infrastructure.adapter.out.shipping.dto.ShippingRequest;
import com.example.order.infrastructure.adapter.out.shipping.dto.ShippingRequest.ShippingItemDto;
import com.example.order.infrastructure.adapter.out.shipping.dto.ShippingResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mapper between domain objects and shipping service DTOs.
 */
@Component
public class ShippingMapper {

    public ShippingRequest toRequest(OrderId orderId, String address, List<OrderItem> items) {
        List<ShippingItemDto> itemDtos = items.stream()
                .map(item -> ShippingItemDto.of(
                        item.getSkuCode().getValue(),
                        item.getQuantity()))
                .toList();

        return ShippingRequest.of(orderId.getValue(), address, itemDtos);
    }

    public ShippingResult toResult(ShippingResponse response) {
        ShippingStatus status = "CREATED".equalsIgnoreCase(response.status())
                ? ShippingStatus.CREATED
                : ShippingStatus.DEFERRED;

        return new ShippingResult(
                response.trackingNumber(),
                status,
                response.message()
        );
    }
}
