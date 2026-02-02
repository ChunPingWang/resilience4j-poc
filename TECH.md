# 技術需求計劃書（TECH）

## Resilience4j Retry + CircuitBreaker + TimeLimiter PoC — 電子商務場景

---

**文件版本**: 1.0  
**建立日期**: 2026-02-02  
**文件狀態**: Draft  
**負責人**: 架構組  
**關聯文件**: PRD.md（業務需求計劃書）

---

## 1. 技術架構概觀

### 1.1 系統架構

本 PoC 採用 Spring Boot 微服務架構，以訂單服務（Order Service）為核心，透過 Resilience4j 對三個下游服務呼叫施加韌性保護。下游服務以 WireMock 模擬各類故障場景。

```
                         ┌──────────────────────────────────────────────┐
                         │            Order Service (PoC)               │
                         │                                              │
  HTTP Request           │  ┌────────────────────────────────────────┐  │
  POST /api/orders ─────►│  │         OrderController                │  │
                         │  │              │                          │  │
                         │  │              ▼                          │  │
                         │  │         OrderService                   │  │
                         │  │          │       │       │              │  │
                         │  │          ▼       ▼       ▼              │  │
                         │  │  ┌───────┐ ┌─────────┐ ┌──────────┐   │  │
                         │  │  │Inventory│ │ Payment │ │ Shipping │   │  │
                         │  │  │ Client │ │ Client  │ │  Client  │   │  │
                         │  │  └───┬───┘ └────┬────┘ └─────┬────┘   │  │
                         │  │      │          │            │         │  │
                         │  │  ┌───▼──────────▼────────────▼──────┐  │  │
                         │  │  │     Resilience4j Decorators       │  │  │
                         │  │  │  TimeLimiter → CircuitBreaker     │  │  │
                         │  │  │       → Retry → HTTP Call         │  │  │
                         │  │  └──────────────────────────────────┘  │  │
                         │  └────────────────────────────────────────┘  │
                         └───────────────┬──────────┬──────────┬───────┘
                                         │          │          │
                              ┌──────────▼┐  ┌──────▼───┐  ┌──▼────────┐
                              │ WireMock  │  │ WireMock │  │ WireMock  │
                              │ Inventory │  │ Payment  │  │ Shipping  │
                              │ :8081     │  │ :8082    │  │ :8083     │
                              └───────────┘  └──────────┘  └───────────┘
```

### 1.2 Resilience4j 裝飾器執行順序

Resilience4j 的裝飾器以洋蔥模型方式層層包裝，最外層最先執行。本 PoC 採用以下順序：

```
Request
  │
  ▼
TimeLimiter          ← 最外層：控制整體超時（含所有重試的總時間）
  │
  ▼
CircuitBreaker       ← 中間層：判斷是否快速失敗
  │
  ▼
Retry                ← 最內層：對單次失敗進行重試
  │
  ▼
Actual HTTP Call     ← 實際呼叫下游服務
```

此順序的設計考量：
- TimeLimiter 在最外層，確保無論重試幾次，整體耗時都不超過上限
- CircuitBreaker 在 Retry 外層，斷路器開啟時直接拒絕，不進入重試迴圈
- Retry 在最內層，每次重試的結果都會被 CircuitBreaker 記錄

---

## 2. 技術棧

### 2.1 核心依賴

| 技術 | 版本 | 用途 |
|------|------|------|
| Java | 17+ | 語言平台 |
| Spring Boot | 3.2.x | 應用框架 |
| Resilience4j | 2.2.x | 韌性模式實現 |
| resilience4j-spring-boot3 | 2.2.x | Spring Boot 自動配置整合 |
| resilience4j-micrometer | 2.2.x | Metrics 匯出 |
| Spring Web (WebClient) | 3.2.x | 非阻塞 HTTP 客戶端 |
| Micrometer + Actuator | 1.12.x | 可觀測性 |
| WireMock | 3.x | 下游服務模擬 |
| JUnit 5 + AssertJ | 5.10.x | 測試框架 |
| Lombok | Latest | 簡化模型程式碼 |

### 2.2 Gradle 依賴配置

```groovy
dependencies {
    // Spring Boot
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-webflux' // for WebClient
    implementation 'org.springframework.boot:spring-boot-starter-actuator'

    // Resilience4j
    implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.2.0'
    implementation 'io.github.resilience4j:resilience4j-reactor:2.2.0'
    implementation 'io.github.resilience4j:resilience4j-micrometer:2.2.0'

    // Micrometer
    implementation 'io.micrometer:micrometer-registry-prometheus'

    // Lombok
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    // Test
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.wiremock:wiremock-standalone:3.3.1'
    testImplementation 'io.github.resilience4j:resilience4j-spring-boot3:2.2.0'
}
```

---

## 3. Resilience4j 配置規格

### 3.1 Retry 配置

針對三個下游服務分別配置 Retry 實例：

| 參數 | inventoryRetry | paymentRetry | shippingRetry | 說明 |
|------|---------------|-------------|--------------|------|
| maxAttempts | 3 | 3 | 2 | 最大嘗試次數（含首次） |
| waitDuration | 500ms | 1000ms | 500ms | 基礎等待時間 |
| enableExponentialBackoff | true | true | false | 指數退避 |
| exponentialBackoffMultiplier | 2.0 | 2.0 | — | 退避倍數 |
| exponentialMaxWaitDuration | 4s | 8s | — | 最大退避時間 |
| retryExceptions | 見 3.1.1 | 見 3.1.1 | 見 3.1.1 | 觸發重試的例外 |
| ignoreExceptions | 見 3.1.2 | 見 3.1.2 | 見 3.1.2 | 不重試的例外 |

#### 3.1.1 可重試例外類別

```java
retryExceptions:
  - java.net.ConnectException
  - java.net.SocketTimeoutException
  - java.io.IOException
  - org.springframework.web.reactive.function.client.WebClientRequestException
  - com.example.order.exception.RetryableServiceException  // 自定義：封裝 5xx
```

#### 3.1.2 不可重試例外類別

```java
ignoreExceptions:
  - com.example.order.exception.NonRetryableServiceException  // 封裝 4xx
  - com.example.order.exception.BusinessException             // 業務邏輯錯誤
  - io.github.resilience4j.circuitbreaker.CallNotPermittedException
```

#### 3.1.3 application.yml 配置

```yaml
resilience4j:
  retry:
    instances:
      inventoryRetry:
        max-attempts: 3
        wait-duration: 500ms
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2.0
        exponential-max-wait-duration: 4s
        retry-exceptions:
          - java.net.ConnectException
          - java.net.SocketTimeoutException
          - java.io.IOException
          - com.example.order.exception.RetryableServiceException
        ignore-exceptions:
          - com.example.order.exception.NonRetryableServiceException
          - com.example.order.exception.BusinessException

      paymentRetry:
        max-attempts: 3
        wait-duration: 1000ms
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2.0
        exponential-max-wait-duration: 8s
        retry-exceptions:
          - java.net.ConnectException
          - java.net.SocketTimeoutException
          - java.io.IOException
          - com.example.order.exception.RetryableServiceException
        ignore-exceptions:
          - com.example.order.exception.NonRetryableServiceException
          - com.example.order.exception.BusinessException

      shippingRetry:
        max-attempts: 2
        wait-duration: 500ms
        retry-exceptions:
          - java.net.ConnectException
          - java.net.SocketTimeoutException
          - java.io.IOException
          - com.example.order.exception.RetryableServiceException
        ignore-exceptions:
          - com.example.order.exception.NonRetryableServiceException
          - com.example.order.exception.BusinessException
```

### 3.2 CircuitBreaker 配置

| 參數 | inventoryCB | paymentCB | shippingCB | 說明 |
|------|------------|----------|-----------|------|
| slidingWindowType | COUNT_BASED | COUNT_BASED | COUNT_BASED | 滑動窗口類型 |
| slidingWindowSize | 10 | 10 | 10 | 窗口大小 |
| minimumNumberOfCalls | 5 | 5 | 5 | 最小呼叫數才開始計算失敗率 |
| failureRateThreshold | 60 | 50 | 60 | 失敗率閾值（%） |
| slowCallRateThreshold | 80 | 80 | 80 | 慢呼叫率閾值（%） |
| slowCallDurationThreshold | 2s | 3s | 2s | 慢呼叫判定時間 |
| waitDurationInOpenState | 30s | 30s | 20s | OPEN 狀態等待時間 |
| permittedNumberOfCallsInHalfOpenState | 5 | 5 | 3 | HALF_OPEN 狀態允許的探測數 |
| automaticTransitionFromOpenToHalfOpenEnabled | true | true | true | 自動轉換至 HALF_OPEN |
| recordExceptions | 見下方 | 見下方 | 見下方 | 記錄為失敗的例外 |
| ignoreExceptions | 見下方 | 見下方 | 見下方 | 不計入失敗率的例外 |

```yaml
resilience4j:
  circuitbreaker:
    instances:
      inventoryCB:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 60
        slow-call-rate-threshold: 80
        slow-call-duration-threshold: 2s
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 5
        automatic-transition-from-open-to-half-open-enabled: true
        record-exceptions:
          - java.net.ConnectException
          - java.net.SocketTimeoutException
          - java.io.IOException
          - java.util.concurrent.TimeoutException
          - com.example.order.exception.RetryableServiceException
        ignore-exceptions:
          - com.example.order.exception.NonRetryableServiceException
          - com.example.order.exception.BusinessException

      paymentCB:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 50
        slow-call-rate-threshold: 80
        slow-call-duration-threshold: 3s
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 5
        automatic-transition-from-open-to-half-open-enabled: true
        record-exceptions:
          - java.net.ConnectException
          - java.net.SocketTimeoutException
          - java.io.IOException
          - java.util.concurrent.TimeoutException
          - com.example.order.exception.RetryableServiceException
        ignore-exceptions:
          - com.example.order.exception.NonRetryableServiceException
          - com.example.order.exception.BusinessException

      shippingCB:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 60
        slow-call-rate-threshold: 80
        slow-call-duration-threshold: 2s
        wait-duration-in-open-state: 20s
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
        record-exceptions:
          - java.net.ConnectException
          - java.net.SocketTimeoutException
          - java.io.IOException
          - java.util.concurrent.TimeoutException
          - com.example.order.exception.RetryableServiceException
        ignore-exceptions:
          - com.example.order.exception.NonRetryableServiceException
          - com.example.order.exception.BusinessException
```

### 3.3 TimeLimiter 配置

| 參數 | inventoryTL | paymentTL | shippingTL | 說明 |
|------|------------|----------|-----------|------|
| timeoutDuration | 4s | 8s | 3s | 超時上限 |
| cancelRunningFuture | true | true | true | 超時後取消底層任務 |

超時設計考量：
- inventoryTL (4s)：涵蓋 3 次重試（500ms + 1s + 2s）+ 正常呼叫時間
- paymentTL (8s)：涵蓋 3 次重試（1s + 2s + 4s）+ 正常呼叫時間，支付需較長容忍
- shippingTL (3s)：僅 2 次重試，物流建單可延後處理，超時後走降級

```yaml
resilience4j:
  timelimiter:
    instances:
      inventoryTL:
        timeout-duration: 4s
        cancel-running-future: true

      paymentTL:
        timeout-duration: 8s
        cancel-running-future: true

      shippingTL:
        timeout-duration: 3s
        cancel-running-future: true
```

---

## 4. 核心程式碼設計

### 4.1 套件結構（Hexagonal Architecture）

```
com.example.order
├── adapter
│   ├── in
│   │   └── web
│   │       ├── OrderController.java
│   │       └── dto
│   │           ├── CreateOrderRequest.java
│   │           └── CreateOrderResponse.java
│   └── out
│       ├── inventory
│       │   └── InventoryServiceAdapter.java
│       ├── payment
│       │   └── PaymentServiceAdapter.java
│       └── shipping
│           └── ShippingServiceAdapter.java
├── application
│   ├── port
│   │   ├── in
│   │   │   └── CreateOrderUseCase.java
│   │   └── out
│   │       ├── InventoryPort.java
│   │       ├── PaymentPort.java
│   │       └── ShippingPort.java
│   └── service
│       └── OrderService.java
├── domain
│   └── model
│       ├── Order.java
│       ├── OrderItem.java
│       └── OrderStatus.java
├── infrastructure
│   ├── config
│   │   ├── Resilience4jConfig.java
│   │   └── WebClientConfig.java
│   └── exception
│       ├── RetryableServiceException.java
│       ├── NonRetryableServiceException.java
│       ├── BusinessException.java
│       └── GlobalExceptionHandler.java
└── OrderApplication.java
```

### 4.2 Resilience4j 註解式整合

在 Adapter 層使用 Resilience4j 的 Spring Boot 註解進行裝飾：

```java
@Component
@Slf4j
public class InventoryServiceAdapter implements InventoryPort {

    private final WebClient webClient;

    // 註解順序：由外到內 → TimeLimiter → CircuitBreaker → Retry
    // fallbackMethod 定義降級邏輯
    @TimeLimiter(name = "inventoryTL", fallbackMethod = "inventoryTimeLimitFallback")
    @CircuitBreaker(name = "inventoryCB", fallbackMethod = "inventoryCircuitBreakerFallback")
    @Retry(name = "inventoryRetry", fallbackMethod = "inventoryRetryFallback")
    @Override
    public CompletableFuture<InventoryResponse> deductInventory(
            String skuCode, int quantity) {

        return webClient.post()
                .uri("/api/inventory/deduct")
                .bodyValue(new InventoryRequest(skuCode, quantity))
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError,
                    resp -> Mono.error(new RetryableServiceException(
                        "Inventory service error: " + resp.statusCode())))
                .onStatus(HttpStatusCode::is4xxClientError,
                    resp -> Mono.error(new NonRetryableServiceException(
                        "Inventory request invalid: " + resp.statusCode())))
                .bodyToMono(InventoryResponse.class)
                .toFuture();
    }

    // --- Fallback Methods ---

    private CompletableFuture<InventoryResponse> inventoryRetryFallback(
            String skuCode, int quantity, RetryableServiceException ex) {
        log.warn("Inventory retry exhausted for SKU={}, attempts failed: {}",
                skuCode, ex.getMessage());
        throw new ServiceUnavailableException("庫存確認暫時無法完成，請稍後重試");
    }

    private CompletableFuture<InventoryResponse> inventoryCircuitBreakerFallback(
            String skuCode, int quantity, CallNotPermittedException ex) {
        log.warn("Inventory circuit breaker OPEN for SKU={}", skuCode);
        throw new ServiceUnavailableException("庫存服務暫時不可用，請稍後重試");
    }

    private CompletableFuture<InventoryResponse> inventoryTimeLimitFallback(
            String skuCode, int quantity, TimeoutException ex) {
        log.warn("Inventory call timed out for SKU={}", skuCode);
        throw new ServiceUnavailableException("庫存確認逾時，請稍後重試");
    }
}
```

### 4.3 支付服務 — 冪等鍵設計

```java
@Component
@Slf4j
public class PaymentServiceAdapter implements PaymentPort {

    private final WebClient webClient;

    @TimeLimiter(name = "paymentTL")
    @CircuitBreaker(name = "paymentCB", fallbackMethod = "paymentFallback")
    @Retry(name = "paymentRetry")
    @Override
    public CompletableFuture<PaymentResponse> processPayment(PaymentRequest request) {

        // 冪等鍵：確保重試不會重複扣款
        String idempotencyKey = request.getIdempotencyKey();

        return webClient.post()
                .uri("/api/payments/charge")
                .header("Idempotency-Key", idempotencyKey)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError,
                    resp -> Mono.error(new RetryableServiceException(
                        "Payment gateway error")))
                .onStatus(status -> status.value() == 409,
                    resp -> Mono.error(new BusinessException("庫存不足，無法扣款")))
                .onStatus(HttpStatusCode::is4xxClientError,
                    resp -> Mono.error(new NonRetryableServiceException(
                        "Payment request invalid")))
                .bodyToMono(PaymentResponse.class)
                .toFuture();
    }

    private CompletableFuture<PaymentResponse> paymentFallback(
            PaymentRequest request, Exception ex) {
        log.warn("Payment fallback triggered for orderId={}, cause={}",
                request.getOrderId(), ex.getClass().getSimpleName());
        throw new ServiceUnavailableException(
                "支付服務暫時不可用，請嘗試其他支付方式或稍後重試");
    }
}
```

### 4.4 物流服務 — 超時降級設計

```java
@Component
@Slf4j
public class ShippingServiceAdapter implements ShippingPort {

    private final WebClient webClient;

    @TimeLimiter(name = "shippingTL", fallbackMethod = "shippingTimeLimitFallback")
    @CircuitBreaker(name = "shippingCB")
    @Retry(name = "shippingRetry")
    @Override
    public CompletableFuture<ShippingResponse> createShipment(
            ShippingRequest request) {

        return webClient.post()
                .uri("/api/shipping/create")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError,
                    resp -> Mono.error(new RetryableServiceException(
                        "Shipping service error")))
                .bodyToMono(ShippingResponse.class)
                .toFuture();
    }

    // 物流超時走特殊降級：訂單照常建立，物流單號延後補發
    private CompletableFuture<ShippingResponse> shippingTimeLimitFallback(
            ShippingRequest request, TimeoutException ex) {
        log.warn("Shipping timed out for orderId={}, will retry asynchronously",
                request.getOrderId());
        return CompletableFuture.completedFuture(
                ShippingResponse.deferred(request.getOrderId(),
                        "物流單號將稍後以通知方式提供"));
    }
}
```

### 4.5 Resilience4j 事件監聽器

```java
@Configuration
@Slf4j
public class Resilience4jEventConfig {

    @Bean
    public RegistryEventConsumer<Retry> retryEventConsumer() {
        return new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<Retry> event) {
                event.getAddedEntry().getEventPublisher()
                    .onRetry(e -> log.info(
                        "[RETRY] name={}, attempt={}, waitDuration={}ms, cause={}",
                        e.getName(), e.getNumberOfRetryAttempts(),
                        e.getWaitInterval().toMillis(),
                        e.getLastThrowable().getClass().getSimpleName()))
                    .onError(e -> log.error(
                        "[RETRY_EXHAUSTED] name={}, totalAttempts={}, lastCause={}",
                        e.getName(), e.getNumberOfRetryAttempts(),
                        e.getLastThrowable().getMessage()));
            }
            // ...
        };
    }

    @Bean
    public RegistryEventConsumer<CircuitBreaker> circuitBreakerEventConsumer() {
        return new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> event) {
                event.getAddedEntry().getEventPublisher()
                    .onStateTransition(e -> log.warn(
                        "[CB_STATE] name={}, from={}, to={}",
                        e.getCircuitBreakerName(),
                        e.getStateTransition().getFromState(),
                        e.getStateTransition().getToState()))
                    .onCallNotPermitted(e -> log.warn(
                        "[CB_REJECTED] name={}", e.getCircuitBreakerName()));
            }
            // ...
        };
    }
}
```

---

## 5. 故障模擬與測試策略

### 5.1 WireMock 故障場景定義

| 場景 ID | 服務 | 模擬行為 | 驗證目標 |
|---------|------|---------|---------|
| F-01 | 庫存 | 前 2 次返回 503，第 3 次返回 200 | Retry 自動復原 |
| F-02 | 庫存 | 返回 400（參數錯誤） | 不觸發重試，直接失敗 |
| F-03 | 庫存 | 返回 409（庫存不足） | 不觸發重試，返回業務錯誤 |
| F-04 | 支付 | 連續 10 次返回 500 | CircuitBreaker 開啟 |
| F-05 | 支付 | CB 開啟後，等待 30s，返回 200 | CB 狀態轉換 OPEN → HALF_OPEN → CLOSED |
| F-06 | 支付 | 連續返回 500 後 CB 開啟，之後的請求 | 快速失敗（< 50ms） |
| F-07 | 物流 | 延遲 5 秒回應 | TimeLimiter 超時觸發 |
| F-08 | 物流 | 延遲 5 秒 + CB 開啟後 | 超時計入失敗率，CB 開啟 |
| F-09 | 全部 | 同時故障 | 三個 CB 獨立運作 |
| F-10 | 支付 | 前 2 次 500 後重試成功（帶冪等鍵） | 冪等鍵正確傳遞 |

### 5.2 WireMock 場景配置範例

```java
@Test
void should_retry_and_succeed_on_transient_inventory_failure() {
    // F-01: 前 2 次 503，第 3 次成功
    stubFor(post(urlEqualTo("/api/inventory/deduct"))
        .inScenario("transient-failure")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(aResponse().withStatus(503))
        .willSetStateTo("SECOND_ATTEMPT"));

    stubFor(post(urlEqualTo("/api/inventory/deduct"))
        .inScenario("transient-failure")
        .whenScenarioStateIs("SECOND_ATTEMPT")
        .willReturn(aResponse().withStatus(503))
        .willSetStateTo("THIRD_ATTEMPT"));

    stubFor(post(urlEqualTo("/api/inventory/deduct"))
        .inScenario("transient-failure")
        .whenScenarioStateIs("THIRD_ATTEMPT")
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {"skuCode":"SKU001","reserved":true,"remainingQty":99}
            """)));

    // Act
    var result = inventoryAdapter.deductInventory("SKU001", 1).join();

    // Assert
    assertThat(result.isReserved()).isTrue();
    verify(3, postRequestedFor(urlEqualTo("/api/inventory/deduct")));
}

@Test
void should_timeout_and_fallback_on_slow_shipping() {
    // F-07: 物流延遲 5 秒
    stubFor(post(urlEqualTo("/api/shipping/create"))
        .willReturn(aResponse()
            .withStatus(200)
            .withFixedDelay(5000) // 5 秒延遲，超過 3 秒的 TimeLimiter
            .withBody("""
                {"trackingNumber":"TRK001"}
            """)));

    // Act
    var result = shippingAdapter.createShipment(
        new ShippingRequest("ORD001", "台北市信義區")).join();

    // Assert - 應走降級，返回 deferred 回應
    assertThat(result.isDeferred()).isTrue();
    assertThat(result.getMessage()).contains("物流單號將稍後以通知方式提供");
}
```

### 5.3 CircuitBreaker 狀態轉換測試

```java
@Test
void should_open_circuit_breaker_when_failure_rate_exceeds_threshold() {
    // F-04: 連續 10 次 500 → CB 開啟
    stubFor(post(urlEqualTo("/api/payments/charge"))
        .willReturn(aResponse().withStatus(500)));

    CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("paymentCB");

    // 觸發 10 次失敗（滑動窗口大小=10，閾值=50%）
    for (int i = 0; i < 10; i++) {
        assertThatThrownBy(() ->
            paymentAdapter.processPayment(createRequest()).join()
        ).hasCauseInstanceOf(RetryableServiceException.class);
    }

    // Assert - CB 應為 OPEN 狀態
    assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

    // F-06: 後續請求應快速失敗
    long start = System.currentTimeMillis();
    assertThatThrownBy(() ->
        paymentAdapter.processPayment(createRequest()).join()
    ).hasCauseInstanceOf(CallNotPermittedException.class);
    long elapsed = System.currentTimeMillis() - start;

    assertThat(elapsed).isLessThan(50); // 快速失敗 < 50ms
}
```

---

## 6. 可觀測性設計

### 6.1 Actuator Endpoints

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, metrics, circuitbreakers, retries, timelimiters, prometheus
  endpoint:
    health:
      show-details: always
  health:
    circuitbreakers:
      enabled: true
  metrics:
    tags:
      application: order-service
    distribution:
      percentiles-histogram:
        resilience4j.circuitbreaker.calls: true
```

### 6.2 關鍵 Metrics 指標

| Metric Name | Type | 用途 |
|------------|------|------|
| `resilience4j_retry_calls_total` | Counter | 各重試實例的呼叫總數（含結果標籤） |
| `resilience4j_circuitbreaker_state` | Gauge | 斷路器當前狀態（0=CLOSED, 1=OPEN, 2=HALF_OPEN） |
| `resilience4j_circuitbreaker_calls_seconds` | Timer | 斷路器呼叫延遲分布 |
| `resilience4j_circuitbreaker_failure_rate` | Gauge | 當前失敗率 |
| `resilience4j_circuitbreaker_not_permitted_calls_total` | Counter | 被斷路器拒絕的請求數 |
| `resilience4j_timelimiter_calls_total` | Counter | 超時/成功的呼叫統計 |

### 6.3 結構化日誌格式

```
2026-02-02 10:30:15.123 WARN  [order-svc] [RETRY] name=inventoryRetry, attempt=2, waitDuration=1000ms, cause=RetryableServiceException
2026-02-02 10:30:16.456 WARN  [order-svc] [CB_STATE] name=paymentCB, from=CLOSED, to=OPEN
2026-02-02 10:30:16.457 WARN  [order-svc] [CB_REJECTED] name=paymentCB
2026-02-02 10:30:20.789 WARN  [order-svc] [TIMEOUT] name=shippingTL, duration=3000ms
```

---

## 7. 例外處理策略

### 7.1 自定義例外體系

```
Exception
├── RetryableServiceException        ← 5xx / 網路錯誤（觸發重試 + 記入CB）
├── NonRetryableServiceException     ← 4xx 非業務錯誤（不重試、不記入CB）
├── BusinessException                ← 業務邏輯錯誤（不重試、不記入CB）
│   ├── InsufficientStockException
│   └── PaymentDeclinedException
└── ServiceUnavailableException      ← 所有 fallback 最終拋出（返回使用者）
```

### 7.2 全域例外處理器

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleServiceUnavailable(
            ServiceUnavailableException ex) {
        return ResponseEntity.status(503)
            .body(new ErrorResponse("SERVICE_UNAVAILABLE", ex.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(422)
            .body(new ErrorResponse("BUSINESS_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ErrorResponse> handleCircuitBreakerOpen(
            CallNotPermittedException ex) {
        return ResponseEntity.status(503)
            .body(new ErrorResponse("CIRCUIT_OPEN",
                "服務暫時不可用，系統正在自動恢復中"));
    }
}
```

---

## 8. 非功能需求

### 8.1 效能要求

| 項目 | 指標 |
|------|------|
| 正常請求 P95 延遲 | ≤ 500ms（不含下游回應時間） |
| 快速失敗 P99 延遲 | ≤ 50ms |
| Resilience4j 自身 overhead | ≤ 5ms per call |
| 記憶體使用 | 每個 CircuitBreaker 實例 < 1MB |

### 8.2 執行緒安全

Resilience4j 所有元件均為 thread-safe，基於 CAS 操作實現無鎖計數。滑動窗口使用 Ring Buffer 結構，不會產生 GC 壓力。PoC 中需驗證在並行呼叫場景下 metrics 數據的正確性。

### 8.3 配置外部化

所有 Resilience4j 參數透過 `application.yml` 配置，支援 Spring Cloud Config 或 ConfigMap 動態更新（PoC 階段以靜態配置為主，但程式碼結構需預留動態更新能力）。

---

## 9. PoC 交付物清單

| # | 交付物 | 格式 | 說明 |
|---|--------|------|------|
| 1 | 原始碼 | Git Repository | 含完整 Spring Boot 專案與測試 |
| 2 | application.yml | YAML | 完整 Resilience4j 配置 |
| 3 | 整合測試報告 | HTML (Gradle Test Report) | 所有故障場景測試結果 |
| 4 | Metrics 截圖 | PNG | Actuator endpoint 的 metrics 輸出 |
| 5 | 參數調優指南 | Markdown | 各參數對行為的影響說明與調整建議 |
| 6 | 架構決策記錄 | Markdown (ADR) | 記錄為何選擇此順序與配置 |

---

## 10. 參考資料

| 資源 | 連結 |
|------|------|
| Resilience4j 官方文件 | https://resilience4j.readme.io/docs |
| Resilience4j Spring Boot3 整合 | https://resilience4j.readme.io/docs/getting-started-3 |
| CircuitBreaker 狀態機詳解 | https://resilience4j.readme.io/docs/circuitbreaker |
| Retry 配置參考 | https://resilience4j.readme.io/docs/retry |
| TimeLimiter 配置參考 | https://resilience4j.readme.io/docs/timeout |
| Spring Boot Actuator Metrics | https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html |
