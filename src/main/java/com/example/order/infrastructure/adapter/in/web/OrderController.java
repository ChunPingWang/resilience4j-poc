package com.example.order.infrastructure.adapter.in.web;

import com.example.order.application.dto.CreateOrderCommand;
import com.example.order.application.port.in.CreateOrderUseCase;
import com.example.order.infrastructure.adapter.in.web.dto.CreateOrderRequest;
import com.example.order.infrastructure.adapter.in.web.dto.CreateOrderResponse;
import com.example.order.infrastructure.adapter.in.web.mapper.OrderWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

/**
 * REST controller for order operations.
 */
@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "訂單管理 API")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final CreateOrderUseCase createOrderUseCase;
    private final OrderWebMapper mapper;

    public OrderController(CreateOrderUseCase createOrderUseCase, OrderWebMapper mapper) {
        this.createOrderUseCase = createOrderUseCase;
        this.mapper = mapper;
    }

    @Operation(
            summary = "建立訂單",
            description = """
                    建立新訂單，流程包含：
                    1. **庫存預留** - 使用 Retry 機制處理暫時性故障
                    2. **支付處理** - 使用 CircuitBreaker 防止雪崩效應
                    3. **物流建單** - 使用 TimeLimiter 超時降級

                    若物流服務超時，訂單仍會成功，物流單號稍後通知。
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "訂單建立成功",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CreateOrderResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "orderId": "550e8400-e29b-41d4-a716-446655440000",
                                      "status": "COMPLETED",
                                      "totalAmount": 3000.00,
                                      "currency": "TWD",
                                      "trackingNumber": "TRK123456789",
                                      "message": "Order created successfully",
                                      "createdAt": "2026-02-02T12:00:00Z"
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "請求參數錯誤",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = """
                                    {
                                      "error": "INVALID_REQUEST",
                                      "message": "SKU code is required",
                                      "timestamp": "2026-02-02T12:00:00Z"
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "業務衝突（如庫存不足）",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = """
                                    {
                                      "error": "INSUFFICIENT_STOCK",
                                      "message": "Insufficient stock for SKU SKU001",
                                      "skuCode": "SKU001",
                                      "timestamp": "2026-02-02T12:00:00Z"
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "服務暫時不可用（韌性機制觸發）",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CreateOrderResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "orderId": "550e8400-e29b-41d4-a716-446655440000",
                                      "status": "FAILED",
                                      "totalAmount": null,
                                      "currency": null,
                                      "trackingNumber": null,
                                      "message": "庫存確認暫時無法完成，請稍後重試",
                                      "createdAt": null
                                    }
                                    """)
                    )
            )
    })
    @PostMapping
    public CompletableFuture<ResponseEntity<CreateOrderResponse>> createOrder(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "訂單建立請求",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CreateOrderRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "items": [
                                        {
                                          "skuCode": "SKU001",
                                          "quantity": 2,
                                          "unitPrice": 1500.00
                                        }
                                      ],
                                      "shippingAddress": "台北市信義區松仁路100號"
                                    }
                                    """)
                    )
            )
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
