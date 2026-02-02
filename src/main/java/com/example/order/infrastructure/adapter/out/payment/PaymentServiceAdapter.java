package com.example.order.infrastructure.adapter.out.payment;

import com.example.order.application.port.out.PaymentPort;
import com.example.order.domain.model.Money;
import com.example.order.domain.model.OrderId;
import com.example.order.infrastructure.adapter.out.payment.dto.PaymentRequest;
import com.example.order.infrastructure.adapter.out.payment.dto.PaymentResponse;
import com.example.order.infrastructure.adapter.out.payment.mapper.PaymentMapper;
import com.example.order.infrastructure.exception.BusinessException;
import com.example.order.infrastructure.exception.NonRetryableServiceException;
import com.example.order.infrastructure.exception.RetryableServiceException;
import com.example.order.infrastructure.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

/**
 * Adapter for payment service with circuit breaker and retry mechanisms.
 * Decorator order: CircuitBreaker → Retry → Actual HTTP Call
 */
@Component
public class PaymentServiceAdapter implements PaymentPort {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceAdapter.class);
    private static final String SERVICE_NAME = "payment";

    private final WebClient webClient;
    private final PaymentMapper mapper;

    public PaymentServiceAdapter(
            @Qualifier("paymentWebClient") WebClient webClient,
            PaymentMapper mapper) {
        this.webClient = webClient;
        this.mapper = mapper;
    }

    @Override
    @CircuitBreaker(name = "paymentCB", fallbackMethod = "processPaymentFallback")
    @Retry(name = "paymentRetry", fallbackMethod = "processPaymentRetryFallback")
    public CompletableFuture<PaymentResult> processPayment(
            OrderId orderId, Money amount, String idempotencyKey) {

        log.debug("Processing payment for order: {}, amount: {}, idempotencyKey: {}",
                orderId, amount, idempotencyKey);

        PaymentRequest request = mapper.toRequest(orderId, amount);

        return webClient.post()
                .uri("/api/payments/charge")
                .header("Idempotency-Key", idempotencyKey)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    int statusCode = response.statusCode().value();
                                    if (statusCode == 409) {
                                        return Mono.error(new BusinessException(
                                                "PAYMENT_CONFLICT",
                                                "支付衝突: " + body));
                                    }
                                    return Mono.error(new NonRetryableServiceException(
                                            SERVICE_NAME, statusCode,
                                            "Payment service error: " + body));
                                }))
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new RetryableServiceException(
                                        SERVICE_NAME, response.statusCode().value(),
                                        "Payment gateway temporarily unavailable"))))
                .bodyToMono(PaymentResponse.class)
                .map(mapper::toResult)
                .toFuture();
    }

    /**
     * Fallback when circuit breaker is open.
     */
    @SuppressWarnings("unused")
    private CompletableFuture<PaymentResult> processPaymentFallback(
            OrderId orderId, Money amount, String idempotencyKey,
            CallNotPermittedException ex) {

        log.warn("Circuit breaker is OPEN for payment service, order: {}", orderId);

        return CompletableFuture.failedFuture(
                new ServiceUnavailableException(
                        SERVICE_NAME,
                        "支付服務暫時不可用，請嘗試其他支付方式或稍後重試"));
    }

    /**
     * Fallback when circuit breaker records a failure (not just open state).
     */
    @SuppressWarnings("unused")
    private CompletableFuture<PaymentResult> processPaymentFallback(
            OrderId orderId, Money amount, String idempotencyKey,
            Throwable throwable) {

        log.error("Payment failed for order: {}, cause: {}", orderId, throwable.getMessage());

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
                        "支付服務暫時不可用，請嘗試其他支付方式或稍後重試",
                        throwable));
    }

    /**
     * Fallback when all retries are exhausted.
     */
    @SuppressWarnings("unused")
    private CompletableFuture<PaymentResult> processPaymentRetryFallback(
            OrderId orderId, Money amount, String idempotencyKey,
            Throwable throwable) {

        log.error("Payment retry exhausted for order: {}, cause: {}", orderId, throwable.getMessage());

        // Re-throw business exceptions without wrapping
        if (throwable instanceof BusinessException) {
            return CompletableFuture.failedFuture(throwable);
        }

        // Re-throw non-retryable exceptions without wrapping
        if (throwable instanceof NonRetryableServiceException) {
            return CompletableFuture.failedFuture(throwable);
        }

        return CompletableFuture.failedFuture(
                new ServiceUnavailableException(
                        SERVICE_NAME,
                        "支付服務暫時不可用，請嘗試其他支付方式或稍後重試",
                        throwable));
    }
}
