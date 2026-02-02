package com.example.order.infrastructure.adapter.out.shipping;

import com.example.order.application.port.out.ShippingPort;
import com.example.order.domain.model.OrderId;
import com.example.order.domain.model.OrderItem;
import com.example.order.infrastructure.adapter.out.shipping.dto.ShippingRequest;
import com.example.order.infrastructure.adapter.out.shipping.dto.ShippingResponse;
import com.example.order.infrastructure.adapter.out.shipping.mapper.ShippingMapper;
import com.example.order.infrastructure.exception.RetryableServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

/**
 * Adapter for shipping service with TimeLimiter, CircuitBreaker, and Retry mechanisms.
 * Decorator order: TimeLimiter → CircuitBreaker → Retry → Actual HTTP Call
 */
@Component
public class ShippingServiceAdapter implements ShippingPort {

    private static final Logger log = LoggerFactory.getLogger(ShippingServiceAdapter.class);
    private static final String SERVICE_NAME = "shipping";

    private final WebClient webClient;
    private final ShippingMapper mapper;

    public ShippingServiceAdapter(
            @Qualifier("shippingWebClient") WebClient webClient,
            ShippingMapper mapper) {
        this.webClient = webClient;
        this.mapper = mapper;
    }

    @Override
    @TimeLimiter(name = "shippingTL", fallbackMethod = "createShipmentTimeoutFallback")
    @CircuitBreaker(name = "shippingCB", fallbackMethod = "createShipmentCircuitBreakerFallback")
    @Retry(name = "shippingRetry")
    public CompletableFuture<ShippingResult> createShipment(
            OrderId orderId, String address, List<OrderItem> items) {

        log.debug("Creating shipment for order: {}, address: {}", orderId, address);

        ShippingRequest request = mapper.toRequest(orderId, address, items);

        return webClient.post()
                .uri("/api/shipping/create")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new RetryableServiceException(
                                        SERVICE_NAME, response.statusCode().value(),
                                        "Shipping service temporarily unavailable"))))
                .bodyToMono(ShippingResponse.class)
                .map(mapper::toResult)
                .toFuture();
    }

    /**
     * Fallback when timeout occurs.
     * Returns a deferred shipping result - the order proceeds but tracking number is provided later.
     */
    @SuppressWarnings("unused")
    private CompletableFuture<ShippingResult> createShipmentTimeoutFallback(
            OrderId orderId, String address, List<OrderItem> items,
            TimeoutException ex) {

        log.warn("Shipping request timed out for order: {}, returning deferred result", orderId);

        return CompletableFuture.completedFuture(
                ShippingResult.deferred("物流單號將稍後以通知方式提供"));
    }

    /**
     * General fallback for timeout and other exceptions.
     */
    @SuppressWarnings("unused")
    private CompletableFuture<ShippingResult> createShipmentTimeoutFallback(
            OrderId orderId, String address, List<OrderItem> items,
            Throwable throwable) {

        log.warn("Shipping request failed for order: {}, cause: {}, returning deferred result",
                orderId, throwable.getMessage());

        return CompletableFuture.completedFuture(
                ShippingResult.deferred("物流單號將稍後以通知方式提供"));
    }

    /**
     * Fallback when circuit breaker is open or records failure.
     */
    @SuppressWarnings("unused")
    private CompletableFuture<ShippingResult> createShipmentCircuitBreakerFallback(
            OrderId orderId, String address, List<OrderItem> items,
            Throwable throwable) {

        log.warn("Shipping circuit breaker triggered for order: {}, returning deferred result", orderId);

        return CompletableFuture.completedFuture(
                ShippingResult.deferred("物流服務暫時不可用，單號將稍後以通知方式提供"));
    }
}
