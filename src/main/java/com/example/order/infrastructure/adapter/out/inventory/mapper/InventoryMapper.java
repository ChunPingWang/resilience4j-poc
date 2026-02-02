package com.example.order.infrastructure.adapter.out.inventory.mapper;

import com.example.order.application.port.out.InventoryPort.InventoryReservationResult;
import com.example.order.domain.model.SkuCode;
import com.example.order.infrastructure.adapter.out.inventory.dto.InventoryRequest;
import com.example.order.infrastructure.adapter.out.inventory.dto.InventoryResponse;
import org.springframework.stereotype.Component;

/**
 * Mapper between domain objects and inventory service DTOs.
 */
@Component
public class InventoryMapper {

    public InventoryRequest toRequest(SkuCode skuCode, int quantity) {
        return InventoryRequest.of(skuCode.getValue(), quantity);
    }

    public InventoryReservationResult toResult(InventoryResponse response) {
        return new InventoryReservationResult(
                response.skuCode(),
                response.reserved(),
                response.remainingQty(),
                null,
                null
        );
    }
}
