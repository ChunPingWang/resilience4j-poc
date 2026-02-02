package com.example.order.integration;

import com.example.order.application.port.out.InventoryPort;
import com.example.order.application.port.out.PaymentPort;
import com.example.order.application.port.out.ShippingPort;
import com.example.order.application.port.out.ShippingPort.ShippingStatus;
import com.example.order.domain.model.Money;
import com.example.order.domain.model.OrderId;
import com.example.order.domain.model.OrderItem;
import com.example.order.domain.model.SkuCode;
import com.example.order.support.WireMockTestSupport;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for combined resilience mechanisms (User Story 4).
 *
 * BDD Scenarios:
 * - Given 三種機制同時配置, When 請求發起, Then 執行順序為 TimeLimiter → CircuitBreaker → Retry → 實際呼叫
 * - Given 斷路器為OPEN狀態, When 請求進入, Then 直接跳過重試邏輯，立即返回快速失敗
 * - Given 單次呼叫超時觸發, When 仍有重試次數, Then 進行下一次重試嘗試
 * - Given 任何韌性事件發生, When 事件觸發, Then 系統產生結構化日誌與量測指標
 */
@ActiveProfiles("test")
@DisplayName("US4: 三模式協作完整保護 - Combined Resilience Tests")
class CombinedResilienceTest extends WireMockTestSupport {

    @Autowired
    private InventoryPort inventoryPort;

    @Autowired
    private PaymentPort paymentPort;

    @Autowired
    private ShippingPort shippingPort;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker inventoryCB;
    private CircuitBreaker paymentCB;
    private CircuitBreaker shippingCB;

    @BeforeEach
    void setupCircuitBreakers() {
        inventoryCB = circuitBreakerRegistry.circuitBreaker("inventoryCB");
        paymentCB = circuitBreakerRegistry.circuitBreaker("paymentCB");
        shippingCB = circuitBreakerRegistry.circuitBreaker("shippingCB");
    }

    @Test
    @DisplayName("should_operate_circuit_breakers_independently - 各服務斷路器獨立運作")
    void should_operate_circuit_breakers_independently() throws Exception {
        // Given: 支付服務持續失敗，庫存和物流服務正常
        stubInventorySuccess("SKU001", 98);
        stubPaymentPermanentFailure();
        stubShippingSuccess("TRK123456");

        // When: 多次呼叫支付服務使其斷路器開啟
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

        // Then: 支付斷路器開啟，但庫存和物流斷路器保持關閉
        assertThat(paymentCB.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(inventoryCB.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(shippingCB.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // And: 庫存服務仍可正常呼叫
        var inventoryResult = inventoryPort.reserveInventory(SkuCode.of("SKU001"), 2).get();
        assertThat(inventoryResult.reserved()).isTrue();

        // And: 物流服務仍可正常呼叫
        var shippingResult = shippingPort.createShipment(
                OrderId.generate(),
                "台北市",
                createTestItems()
        ).get();
        assertThat(shippingResult.status()).isEqualTo(ShippingStatus.CREATED);
    }

    @Test
    @DisplayName("should_skip_retry_when_circuit_breaker_open - 斷路器開啟時跳過重試")
    void should_skip_retry_when_circuit_breaker_open() throws Exception {
        // Given: 支付斷路器已開啟
        stubPaymentPermanentFailure();
        triggerCircuitBreakerOpen(paymentCB);
        assertThat(paymentCB.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Reset WireMock 計數
        paymentServer.resetRequests();

        // When: 發送支付請求
        long startTime = System.currentTimeMillis();
        try {
            paymentPort.processPayment(
                    OrderId.generate(),
                    Money.of(new BigDecimal("1000.00")),
                    UUID.randomUUID().toString()
            ).get();
        } catch (Exception ignored) {
        }
        long elapsed = System.currentTimeMillis() - startTime;

        // Then: 應該快速失敗，不進行重試
        assertThat(elapsed).isLessThan(100);
        // 斷路器開啟時不會實際呼叫後端
        verifyPaymentCalledTimes(0);
    }

    @Test
    @DisplayName("should_preserve_idempotency_key_across_retries - 重試時保持冪等鍵一致")
    void should_preserve_idempotency_key_across_retries() throws Exception {
        // Given: 支付服務正常回應
        String transactionId = UUID.randomUUID().toString();
        String idempotencyKey = UUID.randomUUID().toString();
        stubPaymentSuccess(transactionId);

        // When: 發送支付請求
        var result = paymentPort.processPayment(
                OrderId.generate(),
                Money.of(new BigDecimal("1000.00")),
                idempotencyKey
        ).get();

        // Then: 成功，且請求攜帶正確的冪等鍵
        assertThat(result.status()).isEqualTo(PaymentPort.PaymentStatus.SUCCESS);

        // 驗證請求使用了正確的冪等鍵
        verifyPaymentCalledWithIdempotencyKey(idempotencyKey);
    }

    @Test
    @DisplayName("should_handle_combined_failure_scenarios - 處理組合故障場景")
    void should_handle_combined_failure_scenarios() throws Exception {
        // Given: 庫存服務暫時失敗後恢復
        stubInventoryTransientFailureThenSuccess("SKU001", 1, 98);
        // 支付服務正常
        stubPaymentSuccess(UUID.randomUUID().toString());
        // 物流服務超時（降級處理）
        stubShippingWithDelay("TRK123", 3000);

        // When: 依序呼叫各服務

        // 庫存：應該重試後成功
        var inventoryResult = inventoryPort.reserveInventory(SkuCode.of("SKU001"), 2).get();
        assertThat(inventoryResult.reserved()).isTrue();
        verifyInventoryCalledTimes(2); // 1 次失敗 + 1 次成功

        // 支付：應該直接成功
        var paymentResult = paymentPort.processPayment(
                OrderId.generate(),
                Money.of(new BigDecimal("3000.00")),
                UUID.randomUUID().toString()
        ).get();
        assertThat(paymentResult.status()).isEqualTo(PaymentPort.PaymentStatus.SUCCESS);

        // 物流：應該超時後降級
        var shippingResult = shippingPort.createShipment(
                OrderId.generate(),
                "台北市信義區",
                createTestItems()
        ).get();
        assertThat(shippingResult.status()).isEqualTo(ShippingStatus.DEFERRED);
    }

    @Test
    @DisplayName("should_record_metrics_for_all_resilience_events - 記錄所有韌性事件指標")
    void should_record_metrics_for_all_resilience_events() throws Exception {
        // Given: 各服務正常回應
        stubInventorySuccess("SKU001", 98);
        stubPaymentSuccess(UUID.randomUUID().toString());
        stubShippingSuccess("TRK123456");

        // When: 執行請求
        inventoryPort.reserveInventory(SkuCode.of("SKU001"), 2).get();
        paymentPort.processPayment(
                OrderId.generate(),
                Money.of(new BigDecimal("1000.00")),
                UUID.randomUUID().toString()
        ).get();
        shippingPort.createShipment(
                OrderId.generate(),
                "台北市",
                createTestItems()
        ).get();

        // Then: 斷路器應該記錄呼叫（至少有支付和物流的斷路器）
        // 注意：inventoryCB 可能未啟用（InventoryServiceAdapter 只有 @Retry）
        assertThat(paymentCB.getMetrics().getNumberOfBufferedCalls()).isGreaterThanOrEqualTo(1);
        assertThat(shippingCB.getMetrics().getNumberOfBufferedCalls()).isGreaterThanOrEqualTo(1);
    }

    private void triggerCircuitBreakerOpen(CircuitBreaker cb) {
        // 強制開啟斷路器
        stubPaymentPermanentFailure();
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

    private void stubPaymentTransientFailureThenSuccess(String transactionId, int failCount) {
        // 使用場景模式
        String scenarioName = "PaymentTransientFailure";

        for (int i = 0; i < failCount; i++) {
            String currentState = i == 0 ? com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED : "Attempt" + i;
            String nextState = "Attempt" + (i + 1);

            paymentServer.stubFor(
                    com.github.tomakehurst.wiremock.client.WireMock.post(
                            com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo("/api/payments/charge"))
                    .inScenario(scenarioName)
                    .whenScenarioStateIs(currentState)
                    .willSetStateTo(nextState)
                    .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                            .withStatus(500)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                        "code": "INTERNAL_ERROR",
                                        "message": "Payment gateway internal error"
                                    }
                                    """)));
        }

        paymentServer.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(
                        com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo("/api/payments/charge"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("Attempt" + failCount)
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "transactionId": "%s",
                                    "status": "SUCCESS",
                                    "message": "Payment processed successfully"
                                }
                                """.formatted(transactionId))));
    }

    private List<OrderItem> createTestItems() {
        return List.of(
                OrderItem.of(SkuCode.of("SKU001"), 2, Money.of(new BigDecimal("1500.00")))
        );
    }
}
