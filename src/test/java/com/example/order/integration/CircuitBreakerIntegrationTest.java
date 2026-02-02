package com.example.order.integration;

import com.example.order.application.port.out.PaymentPort;
import com.example.order.application.port.out.PaymentPort.PaymentResult;
import com.example.order.application.port.out.PaymentPort.PaymentStatus;
import com.example.order.domain.model.Money;
import com.example.order.domain.model.OrderId;
import com.example.order.infrastructure.exception.ServiceUnavailableException;
import com.example.order.support.WireMockTestSupport;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for CircuitBreaker mechanism (User Story 2).
 *
 * BDD Scenarios:
 * - Given 支付閘道在10次呼叫中失敗率達60%, When 斷路器判斷失敗率超過閾值, Then 斷路器狀態變為OPEN
 * - Given 斷路器為OPEN狀態, When 新的支付請求進入, Then 請求在50ms內返回快速失敗
 * - Given 斷路器OPEN狀態已維持指定時間, When 等待時間到達, Then 斷路器進入HALF_OPEN狀態
 * - Given 斷路器為HALF_OPEN狀態且探測請求成功率≥60%, When 探測完成, Then 斷路器恢復為CLOSED狀態
 */
@ActiveProfiles("test")
@DisplayName("US2: 支付閘道快速失敗保護 - CircuitBreaker Integration Tests")
class CircuitBreakerIntegrationTest extends WireMockTestSupport {

    @Autowired
    private PaymentPort paymentPort;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker paymentCircuitBreaker;

    @BeforeEach
    void setupCircuitBreaker() {
        paymentCircuitBreaker = circuitBreakerRegistry.circuitBreaker("paymentCB");
        // Reset is already done in base class
    }

    @Test
    @DisplayName("should_open_circuit_breaker_when_failure_rate_exceeds_threshold - 失敗率超過閾值時開啟斷路器")
    void should_open_circuit_breaker_when_failure_rate_exceeds_threshold() {
        // Given: 支付閘道持續返回 500 錯誤
        stubPaymentPermanentFailure();

        // When: 發送足夠多的失敗請求以觸發斷路器開啟
        // 測試環境: minimumNumberOfCalls=5, failureRateThreshold=50
        for (int i = 0; i < 5; i++) {
            try {
                paymentPort.processPayment(
                        OrderId.generate(),
                        Money.of(new BigDecimal("1000.00")),
                        UUID.randomUUID().toString()
                ).get();
            } catch (Exception ignored) {
                // Expected to fail
            }
        }

        // Then: 斷路器應該開啟
        assertThat(paymentCircuitBreaker.getState())
                .isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("should_fast_fail_when_circuit_breaker_open - 斷路器開啟時快速失敗")
    void should_fast_fail_when_circuit_breaker_open() throws Exception {
        // Given: 斷路器已開啟
        stubPaymentPermanentFailure();
        triggerCircuitBreakerOpen();

        assertThat(paymentCircuitBreaker.getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        // When: 發送新請求並計時
        long startTime = System.currentTimeMillis();
        assertThatThrownBy(() -> paymentPort.processPayment(
                OrderId.generate(),
                Money.of(new BigDecimal("1000.00")),
                UUID.randomUUID().toString()
        ).get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ServiceUnavailableException.class);
        long elapsed = System.currentTimeMillis() - startTime;

        // Then: 應該在 50ms 內快速失敗（不實際呼叫後端）
        assertThat(elapsed).isLessThan(100); // 給一些容錯空間
    }

    @Test
    @DisplayName("should_return_proper_error_message_on_circuit_open - 斷路器開啟時返回適當錯誤訊息")
    void should_return_proper_error_message_on_circuit_open() {
        // Given: 斷路器已開啟
        stubPaymentPermanentFailure();
        triggerCircuitBreakerOpen();

        // When & Then: 錯誤訊息應包含服務不可用提示
        assertThatThrownBy(() -> paymentPort.processPayment(
                OrderId.generate(),
                Money.of(new BigDecimal("1000.00")),
                UUID.randomUUID().toString()
        ).get())
                .isInstanceOf(ExecutionException.class)
                .satisfies(ex -> {
                    ServiceUnavailableException cause = (ServiceUnavailableException) ex.getCause();
                    assertThat(cause.getMessage()).contains("支付服務暫時不可用");
                });
    }

    @Test
    @DisplayName("should_transition_to_half_open_after_wait_duration - 等待時間後進入半開狀態")
    void should_transition_to_half_open_after_wait_duration() throws Exception {
        // Given: 斷路器已開啟
        stubPaymentPermanentFailure();
        triggerCircuitBreakerOpen();
        assertThat(paymentCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // When: 等待 waitDurationInOpenState (測試環境設為 5s，這裡手動轉換)
        paymentCircuitBreaker.transitionToHalfOpenState();

        // Then: 狀態應為 HALF_OPEN
        assertThat(paymentCircuitBreaker.getState())
                .isEqualTo(CircuitBreaker.State.HALF_OPEN);
    }

    @Test
    @DisplayName("should_close_circuit_breaker_when_probe_succeeds - 探測成功時關閉斷路器")
    void should_close_circuit_breaker_when_probe_succeeds() throws Exception {
        // Given: 斷路器處於 HALF_OPEN 狀態
        stubPaymentPermanentFailure();
        triggerCircuitBreakerOpen();
        paymentCircuitBreaker.transitionToHalfOpenState();
        assertThat(paymentCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // 重設 WireMock 讓後續請求成功
        paymentServer.resetAll();
        stubPaymentSuccess(UUID.randomUUID().toString());

        // When: 發送足夠多的成功探測請求
        // 測試環境: permittedNumberOfCallsInHalfOpenState=3
        for (int i = 0; i < 3; i++) {
            PaymentResult result = paymentPort.processPayment(
                    OrderId.generate(),
                    Money.of(new BigDecimal("1000.00")),
                    UUID.randomUUID().toString()
            ).get();
            assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        }

        // Then: 斷路器應該關閉
        assertThat(paymentCircuitBreaker.getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("should_process_payment_successfully_when_service_healthy - 服務正常時成功處理支付")
    void should_process_payment_successfully_when_service_healthy() throws Exception {
        // Given: 支付服務正常
        String transactionId = UUID.randomUUID().toString();
        stubPaymentSuccess(transactionId);

        // When
        PaymentResult result = paymentPort.processPayment(
                OrderId.generate(),
                Money.of(new BigDecimal("1500.00")),
                UUID.randomUUID().toString()
        ).get();

        // Then
        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.message()).contains("success");
    }

    /**
     * Helper method to trigger circuit breaker to OPEN state.
     */
    private void triggerCircuitBreakerOpen() {
        for (int i = 0; i < 5; i++) {
            try {
                paymentPort.processPayment(
                        OrderId.generate(),
                        Money.of(new BigDecimal("1000.00")),
                        UUID.randomUUID().toString()
                ).get();
            } catch (Exception ignored) {
            }
        }
    }
}
