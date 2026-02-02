package com.example.order.infrastructure.adapter.in.web;

import com.example.order.application.dto.CreateOrderCommand;
import com.example.order.application.dto.OrderResult;
import com.example.order.application.port.in.CreateOrderUseCase;
import com.example.order.infrastructure.adapter.in.web.dto.CreateOrderRequest;
import com.example.order.infrastructure.adapter.in.web.dto.CreateOrderResponse;
import com.example.order.infrastructure.adapter.in.web.mapper.OrderWebMapper;
import com.example.order.infrastructure.service.IdempotencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for order operations.
 * Supports idempotency via X-Idempotency-Key header.
 */
@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "訂單管理 API")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final CreateOrderUseCase createOrderUseCase;
    private final OrderWebMapper mapper;
    private final IdempotencyService idempotencyService;

    public OrderController(
            CreateOrderUseCase createOrderUseCase,
            OrderWebMapper mapper,
            IdempotencyService idempotencyService) {
        this.createOrderUseCase = createOrderUseCase;
        this.mapper = mapper;
        this.idempotencyService = idempotencyService;
    }

    @Operation(
            summary = "建立訂單",
            description = """
                    建立新訂單，流程包含：
                    1. **庫存預留** - 使用 Retry 機制處理暫時性故障
                    2. **支付處理** - 使用 CircuitBreaker 防止雪崩效應
                    3. **物流建單** - 使用 TimeLimiter 超時降級

                    若物流服務超時，訂單仍會成功，物流單號稍後通知。

                    **冪等性支援**：提供 X-Idempotency-Key header 可確保重複請求安全。
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
                    responseCode = "200",
                    description = "冪等請求 - 返回先前結果",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CreateOrderResponse.class)
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
                    description = "業務衝突（如庫存不足）或請求處理中",
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
            @Parameter(
                    description = "冪等鍵 - 用於確保請求安全重試。若不提供，系統將自動生成。",
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
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

        // Generate idempotency key if not provided
        final String effectiveIdempotencyKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey
                : UUID.randomUUID().toString();

        log.info("Received order request with {} items, idempotencyKey: {}",
                request.items().size(), effectiveIdempotencyKey);

        // Strategy 2: Check for existing result (idempotency)
        Optional<OrderResult> existingResult = idempotencyService.getExistingResult(effectiveIdempotencyKey);
        if (existingResult.isPresent()) {
            log.info("Returning cached result for idempotency key: {}", effectiveIdempotencyKey);
            CreateOrderResponse response = mapper.toResponse(existingResult.get());
            return CompletableFuture.completedFuture(ResponseEntity.ok(response));
        }

        // Check if request is currently in progress
        if (idempotencyService.isInProgress(effectiveIdempotencyKey)) {
            log.warn("Request already in progress for idempotency key: {}", effectiveIdempotencyKey);
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(CreateOrderResponse.error("Request is already being processed"))
            );
        }

        CreateOrderCommand command = mapper.toCommand(request);

        // Mark as in progress before processing
        String orderId = UUID.randomUUID().toString();
        if (!idempotencyService.markInProgress(effectiveIdempotencyKey, orderId)) {
            // Race condition - another request started processing
            log.warn("Failed to mark in progress, request already exists: {}", effectiveIdempotencyKey);
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(CreateOrderResponse.error("Request is already being processed"))
            );
        }

        return createOrderUseCase.createOrder(command)
                .thenApply(result -> {
                    // Save result for future idempotent requests
                    idempotencyService.saveResult(effectiveIdempotencyKey, result);

                    CreateOrderResponse response = mapper.toResponse(result);

                    if ("FAILED".equals(result.status())) {
                        log.warn("Order creation failed: {}", result.message());
                        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
                    }

                    log.info("Order created successfully: {}", result.orderId());
                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                })
                .exceptionally(throwable -> {
                    log.error("Order creation failed with exception", throwable);
                    idempotencyService.markFailed(effectiveIdempotencyKey);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(CreateOrderResponse.error("Internal server error: " + throwable.getMessage()));
                });
    }

    @Operation(
            summary = "查詢訂單狀態",
            description = "根據訂單 ID 查詢訂單當前狀態"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "查詢成功"),
            @ApiResponse(responseCode = "404", description = "訂單不存在")
    })
    @GetMapping("/{orderId}")
    public ResponseEntity<CreateOrderResponse> getOrder(
            @Parameter(description = "訂單 ID", required = true)
            @PathVariable String orderId) {
        // TODO: Implement order query from database
        log.info("Query order: {}", orderId);
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(CreateOrderResponse.error("Order query not yet implemented"));
    }
}
