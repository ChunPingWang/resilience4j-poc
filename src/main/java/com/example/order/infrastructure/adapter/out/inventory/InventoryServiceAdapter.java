package com.example.order.infrastructure.adapter.out.inventory;

import com.example.order.application.port.out.InventoryPort;
import com.example.order.domain.model.SkuCode;
import com.example.order.infrastructure.adapter.out.inventory.dto.InventoryRequest;
import com.example.order.infrastructure.adapter.out.inventory.dto.InventoryResponse;
import com.example.order.infrastructure.adapter.out.inventory.mapper.InventoryMapper;
import com.example.order.infrastructure.exception.BusinessException;
import com.example.order.infrastructure.exception.NonRetryableServiceException;
import com.example.order.infrastructure.exception.RetryableServiceException;
import com.example.order.infrastructure.exception.ServiceUnavailableException;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

/**
 * Adapter for inventory service with retry mechanism.
 */
@Component
public class InventoryServiceAdapter implements InventoryPort {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceAdapter.class);
    private static final String SERVICE_NAME = "inventory";

    private final WebClient webClient;
    private final InventoryMapper mapper;

    public InventoryServiceAdapter(
            @Qualifier("inventoryWebClient") WebClient webClient,
            InventoryMapper mapper) {
        this.webClient = webClient;
        this.mapper = mapper;
    }

    @Override
    @Retry(name = "inventoryRetry", fallbackMethod = "reserveInventoryFallback")
    public CompletableFuture<InventoryReservationResult> reserveInventory(SkuCode skuCode, int quantity) {
        log.debug("Reserving inventory for SKU: {}, quantity: {}", skuCode, quantity);

        InventoryRequest request = mapper.toRequest(skuCode, quantity);

        return webClient.post()
                .uri("/api/inventory/deduct")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    int statusCode = response.statusCode().value();
                                    if (statusCode == 409) {
                                        return Mono.error(new BusinessException(
                                                "INSUFFICIENT_STOCK",
                                                "庫存不足: " + skuCode.getValue()));
                                    }
                                    return Mono.error(new NonRetryableServiceException(
                                            SERVICE_NAME, statusCode,
                                            "Inventory service error: " + body));
                                }))
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new RetryableServiceException(
                                        SERVICE_NAME, response.statusCode().value(),
                                        "Inventory service temporarily unavailable"))))
                .bodyToMono(InventoryResponse.class)
                .map(mapper::toResult)
                .toFuture();
    }

    /**
     * Fallback method when all retries are exhausted.
     */
    @SuppressWarnings("unused")
    private CompletableFuture<InventoryReservationResult> reserveInventoryFallback(
            SkuCode skuCode, int quantity, Throwable throwable) {

        log.error("Inventory reservation failed after retries for SKU: {}, cause: {}",
                skuCode, throwable.getMessage());

        // Re-throw business exceptions without wrapping
        if (throwable instanceof BusinessException) {
            return CompletableFuture.failedFuture(throwable);
        }

        // Re-throw non-retryable exceptions without wrapping
        if (throwable instanceof NonRetryableServiceException) {
            return CompletableFuture.failedFuture(throwable);
        }

        // Wrap other exceptions in ServiceUnavailableException
        return CompletableFuture.failedFuture(
                new ServiceUnavailableException(
                        SERVICE_NAME,
                        "庫存確認暫時無法完成，請稍後重試",
                        throwable));
    }
}
