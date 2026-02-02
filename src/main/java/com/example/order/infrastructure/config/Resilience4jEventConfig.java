package com.example.order.infrastructure.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Resilience4j event logging.
 */
@Configuration
public class Resilience4jEventConfig {

    private static final Logger log = LoggerFactory.getLogger(Resilience4jEventConfig.class);

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;

    public Resilience4jEventConfig(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            TimeLimiterRegistry timeLimiterRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.timeLimiterRegistry = timeLimiterRegistry;
    }

    @PostConstruct
    public void registerEventListeners() {
        registerCircuitBreakerEvents();
        registerRetryEvents();
        registerTimeLimiterEvents();
    }

    private void registerCircuitBreakerEvents() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(this::registerCircuitBreakerEventListener);
        circuitBreakerRegistry.getEventPublisher()
                .onEntryAdded(event -> registerCircuitBreakerEventListener(event.getAddedEntry()));
    }

    private void registerCircuitBreakerEventListener(CircuitBreaker circuitBreaker) {
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.info(
                        "[CB_STATE] name={}, from={}, to={}",
                        event.getCircuitBreakerName(),
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()))
                .onError(event -> log.warn(
                        "[CB_ERROR] name={}, duration={}ms, error={}",
                        event.getCircuitBreakerName(),
                        event.getElapsedDuration().toMillis(),
                        event.getThrowable().getMessage()))
                .onSuccess(event -> log.debug(
                        "[CB_SUCCESS] name={}, duration={}ms",
                        event.getCircuitBreakerName(),
                        event.getElapsedDuration().toMillis()))
                .onSlowCallRateExceeded(event -> log.warn(
                        "[CB_SLOW_RATE] name={}, slowCallRate={}%",
                        event.getCircuitBreakerName(),
                        event.getSlowCallRate()))
                .onFailureRateExceeded(event -> log.warn(
                        "[CB_FAIL_RATE] name={}, failureRate={}%",
                        event.getCircuitBreakerName(),
                        event.getFailureRate()))
                .onCallNotPermitted(event -> log.warn(
                        "[CB_REJECTED] name={}, circuit is OPEN",
                        event.getCircuitBreakerName()));
    }

    private void registerRetryEvents() {
        retryRegistry.getAllRetries().forEach(this::registerRetryEventListener);
        retryRegistry.getEventPublisher()
                .onEntryAdded(event -> registerRetryEventListener(event.getAddedEntry()));
    }

    private void registerRetryEventListener(Retry retry) {
        retry.getEventPublisher()
                .onRetry(event -> log.info(
                        "[RETRY] name={}, attempt={}, waitDuration={}ms, cause={}",
                        event.getName(),
                        event.getNumberOfRetryAttempts(),
                        event.getWaitInterval().toMillis(),
                        event.getLastThrowable() != null ? event.getLastThrowable().getMessage() : "N/A"))
                .onError(event -> log.error(
                        "[RETRY_EXHAUSTED] name={}, attempts={}, error={}",
                        event.getName(),
                        event.getNumberOfRetryAttempts(),
                        event.getLastThrowable().getMessage()))
                .onSuccess(event -> log.debug(
                        "[RETRY_SUCCESS] name={}, attempts={}",
                        event.getName(),
                        event.getNumberOfRetryAttempts()))
                .onIgnoredError(event -> log.debug(
                        "[RETRY_IGNORED] name={}, error={} (not retryable)",
                        event.getName(),
                        event.getLastThrowable().getMessage()));
    }

    private void registerTimeLimiterEvents() {
        timeLimiterRegistry.getAllTimeLimiters().forEach(this::registerTimeLimiterEventListener);
        timeLimiterRegistry.getEventPublisher()
                .onEntryAdded(event -> registerTimeLimiterEventListener(event.getAddedEntry()));
    }

    private void registerTimeLimiterEventListener(TimeLimiter timeLimiter) {
        timeLimiter.getEventPublisher()
                .onTimeout(event -> log.warn(
                        "[TIMEOUT] name={}, timeout occurred",
                        event.getTimeLimiterName()))
                .onSuccess(event -> log.debug(
                        "[TL_SUCCESS] name={}, completed within time limit",
                        event.getTimeLimiterName()))
                .onError(event -> log.error(
                        "[TL_ERROR] name={}, error={}",
                        event.getTimeLimiterName(),
                        event.getThrowable().getMessage()));
    }
}
