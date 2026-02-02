package com.example.order.integration;

import com.example.order.application.port.out.InventoryPort;
import com.example.order.application.port.out.InventoryPort.InventoryReservationResult;
import com.example.order.domain.model.SkuCode;
import com.example.order.infrastructure.exception.BusinessException;
import com.example.order.infrastructure.exception.NonRetryableServiceException;
import com.example.order.infrastructure.exception.ServiceUnavailableException;
import com.example.order.support.WireMockTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for Retry mechanism (User Story 1).
 *
 * BDD Scenarios:
 * - Given 庫存服務首次請求返回 503 錯誤, When 系統發起庫存扣減請求, Then 系統自動重試
 * - Given 庫存服務前兩次返回 503、第三次返回成功, When 系統進行重試, Then 最終扣減成功
 * - Given 庫存服務返回 400（參數錯誤）, When 系統發起請求, Then 系統不進行重試
 * - Given 庫存服務返回 409（庫存不足）, When 系統發起請求, Then 系統不進行重試
 * - Given 庫存服務連續三次皆返回 503, When 重試次數耗盡, Then 系統返回錯誤訊息
 */
@ActiveProfiles("test")
@DisplayName("US1: 暫時性故障自動復原 - Retry Integration Tests")
class RetryIntegrationTest extends WireMockTestSupport {

    @Autowired
    private InventoryPort inventoryPort;

    @Test
    @DisplayName("should_retry_and_succeed_on_transient_inventory_failure - 庫存服務前兩次返回503後成功")
    void should_retry_and_succeed_on_transient_inventory_failure() throws Exception {
        // Given: 庫存服務前兩次返回 503、第三次返回成功
        String skuCode = "SKU001";
        stubInventoryTransientFailureThenSuccess(skuCode, 2, 98);

        // When: 系統發起庫存扣減請求
        InventoryReservationResult result = inventoryPort
                .reserveInventory(SkuCode.of(skuCode), 2)
                .get();

        // Then: 最終扣減成功
        assertThat(result.reserved()).isTrue();
        assertThat(result.skuCode()).isEqualTo(skuCode);
        assertThat(result.remainingQuantity()).isEqualTo(98);

        // And: 驗證重試了 3 次（2 次失敗 + 1 次成功）
        verifyInventoryCalledTimes(3);
    }

    @Test
    @DisplayName("should_not_retry_on_4xx_business_error - 庫存服務返回400不重試")
    void should_not_retry_on_4xx_business_error() {
        // Given: 庫存服務返回 400 Bad Request
        stubInventoryBadRequest();

        // When & Then: 系統不進行重試，直接返回錯誤
        assertThatThrownBy(() -> inventoryPort
                .reserveInventory(SkuCode.of("SKU001"), 2)
                .get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(NonRetryableServiceException.class);

        // And: 只呼叫了一次（無重試）
        verifyInventoryCalledTimes(1);
    }

    @Test
    @DisplayName("should_not_retry_on_409_insufficient_stock - 庫存不足不重試")
    void should_not_retry_on_409_insufficient_stock() {
        // Given: 庫存服務返回 409 庫存不足
        stubInventoryInsufficientStock("SKU001", 0);

        // When & Then: 系統不進行重試，返回業務錯誤
        assertThatThrownBy(() -> inventoryPort
                .reserveInventory(SkuCode.of("SKU001"), 100)
                .get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException cause = (BusinessException) ex.getCause();
                    assertThat(cause.getErrorCode()).isEqualTo("INSUFFICIENT_STOCK");
                    assertThat(cause.getMessage()).contains("庫存不足");
                });

        // And: 只呼叫了一次（無重試）
        verifyInventoryCalledTimes(1);
    }

    @Test
    @DisplayName("should_return_error_message_when_retry_exhausted - 重試耗盡返回錯誤")
    void should_return_error_message_when_retry_exhausted() {
        // Given: 庫存服務持續返回 503
        stubInventoryPermanentFailure();

        // When & Then: 重試次數耗盡後返回 ServiceUnavailableException
        assertThatThrownBy(() -> inventoryPort
                .reserveInventory(SkuCode.of("SKU001"), 2)
                .get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("庫存確認暫時無法完成");

        // And: 驗證重試了 3 次（maxAttempts = 3）
        verifyInventoryCalledTimes(3);
    }

    @Test
    @DisplayName("should_use_exponential_backoff_between_retries - 使用指數退避策略")
    void should_use_exponential_backoff_between_retries() throws Exception {
        // Given: 庫存服務前兩次返回 503、第三次返回成功
        String skuCode = "SKU001";
        stubInventoryTransientFailureThenSuccess(skuCode, 2, 98);

        // When: 記錄開始時間並發起請求
        long startTime = System.currentTimeMillis();
        InventoryReservationResult result = inventoryPort
                .reserveInventory(SkuCode.of(skuCode), 2)
                .get();
        long elapsedTime = System.currentTimeMillis() - startTime;

        // Then: 成功且耗時符合指數退避預期
        // 測試環境：wait-duration=100ms, multiplier=2.0
        // 第一次重試等待 100ms，第二次等待 200ms，總計至少 300ms
        assertThat(result.reserved()).isTrue();
        assertThat(elapsedTime).isGreaterThanOrEqualTo(200); // 至少等待 200ms (100 + 200 with some tolerance)

        verifyInventoryCalledTimes(3);
    }
}
