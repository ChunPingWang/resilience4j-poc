# Resilience4j PoC - 電子商務訂單服務韌性機制

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](./gradlew)
[![Java](https://img.shields.io/badge/Java-17+-blue)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.x-green)](https://spring.io/projects/spring-boot)
[![Resilience4j](https://img.shields.io/badge/Resilience4j-2.2.x-orange)](https://resilience4j.readme.io/)

## 專案目的

本專案是一個 **Resilience4j 韌性機制概念驗證 (PoC)**，展示如何在微服務架構中實現服務韌性。透過模擬電子商務訂單服務，示範三種核心韌性模式：

| 模式 | 目的 | 應用場景 |
|------|------|----------|
| **Retry（重試）** | 處理暫時性故障 | 庫存服務網路抖動 |
| **CircuitBreaker（斷路器）** | 防止雪崩效應 | 支付閘道持續故障 |
| **TimeLimiter（超時控制）** | 保護資源不被阻塞 | 物流服務慢回應 |

## 專案狀態

| 階段 | 狀態 | 說明 |
|------|------|------|
| 規格設計 | ✅ 完成 | spec.md, plan.md, tasks.md |
| Domain Layer | ✅ 完成 | Order, OrderItem, Value Objects |
| Application Layer | ✅ 完成 | Ports, Use Cases, OrderService |
| Infrastructure Layer | ✅ 完成 | Adapters, Resilience4j 配置 |
| 單元測試 | ✅ 完成 | 36 項測試全數通過 |
| API 文件 | ✅ 完成 | Swagger UI |

---

## 快速開始

### 環境需求

- Java 17+
- Gradle 8.x

### 建置與執行

```bash
# 建置專案
./gradlew build

# 執行測試
./gradlew test

# 啟動服務
./gradlew bootRun
```

### 存取服務

| 端點 | URL |
|------|-----|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| API Docs | http://localhost:8080/api-docs |
| Health Check | http://localhost:8080/actuator/health |
| Prometheus Metrics | http://localhost:8080/actuator/prometheus |

### 測試 API

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      {"skuCode": "SKU001", "quantity": 2, "unitPrice": 1500.00}
    ],
    "shippingAddress": "台北市信義區松仁路100號"
  }'
```

---

## 系統架構

### C4 Model - System Context

```mermaid
C4Context
    title System Context Diagram - Order Service

    Person(customer, "消費者", "結帳購買商品的用戶")
    Person(ops, "維運人員", "監控系統健康狀態")

    System(orderService, "Order Service", "訂單服務<br/>處理結帳流程<br/>實現韌性機制")

    System_Ext(inventoryService, "Inventory Service", "庫存服務<br/>管理商品庫存")
    System_Ext(paymentService, "Payment Gateway", "支付閘道<br/>處理金流")
    System_Ext(shippingService, "Shipping Service", "物流服務<br/>建立出貨單")
    System_Ext(prometheus, "Prometheus", "指標收集<br/>監控告警")

    Rel(customer, orderService, "建立訂單", "HTTPS/JSON")
    Rel(ops, orderService, "監控健康狀態", "Actuator")
    Rel(orderService, inventoryService, "預留庫存", "HTTP + Retry")
    Rel(orderService, paymentService, "處理支付", "HTTP + CircuitBreaker")
    Rel(orderService, shippingService, "建立出貨單", "HTTP + TimeLimiter")
    Rel(orderService, prometheus, "暴露指標", "Prometheus")
```

### C4 Model - Container Diagram

```mermaid
C4Container
    title Container Diagram - Order Service

    Person(customer, "消費者")

    Container_Boundary(orderService, "Order Service") {
        Container(webLayer, "Web Layer", "Spring WebFlux", "REST API 端點<br/>請求驗證")
        Container(appLayer, "Application Layer", "Java", "Use Cases<br/>流程編排")
        Container(domainLayer, "Domain Layer", "Java", "業務邏輯<br/>領域模型")
        Container(infraLayer, "Infrastructure Layer", "Spring + Resilience4j", "外部服務整合<br/>韌性機制")
    }

    System_Ext(inventory, "Inventory Service")
    System_Ext(payment, "Payment Gateway")
    System_Ext(shipping, "Shipping Service")

    Rel(customer, webLayer, "POST /api/orders", "JSON")
    Rel(webLayer, appLayer, "CreateOrderCommand")
    Rel(appLayer, domainLayer, "Order Aggregate")
    Rel(appLayer, infraLayer, "Ports")
    Rel(infraLayer, inventory, "Retry", "HTTP")
    Rel(infraLayer, payment, "CircuitBreaker", "HTTP")
    Rel(infraLayer, shipping, "TimeLimiter", "HTTP")
```

### 六角形架構 (Hexagonal Architecture)

```mermaid
graph TB
    subgraph "Infrastructure Layer (外層)"
        subgraph "Inbound Adapters"
            REST["OrderController<br/>(REST API)"]
        end

        subgraph "Outbound Adapters"
            INV["InventoryServiceAdapter<br/>@Retry"]
            PAY["PaymentServiceAdapter<br/>@CircuitBreaker + @Retry"]
            SHIP["ShippingServiceAdapter<br/>@TimeLimiter + @CircuitBreaker + @Retry"]
        end

        subgraph "Configuration"
            R4J["Resilience4jConfig"]
            WC["WebClientConfig"]
        end
    end

    subgraph "Application Layer (中層)"
        UC["CreateOrderUseCase"]
        SVC["OrderService"]
        subgraph "Ports"
            PIN["CreateOrderUseCase<br/>(Inbound Port)"]
            POUT1["InventoryPort<br/>(Outbound Port)"]
            POUT2["PaymentPort<br/>(Outbound Port)"]
            POUT3["ShippingPort<br/>(Outbound Port)"]
        end
    end

    subgraph "Domain Layer (核心)"
        AGG["Order<br/>(Aggregate Root)"]
        ENT["OrderItem<br/>(Entity)"]
        VO1["OrderId"]
        VO2["Money"]
        VO3["SkuCode"]
        ENUM["OrderStatus"]
    end

    REST --> PIN
    PIN --> SVC
    SVC --> AGG
    SVC --> POUT1
    SVC --> POUT2
    SVC --> POUT3
    INV -.-> POUT1
    PAY -.-> POUT2
    SHIP -.-> POUT3
    AGG --> ENT
    AGG --> VO1
    AGG --> VO2
    ENT --> VO3
    AGG --> ENUM

    style AGG fill:#f9f,stroke:#333
    style SVC fill:#bbf,stroke:#333
    style REST fill:#bfb,stroke:#333
```

---

## 領域模型

### ER Diagram

```mermaid
erDiagram
    ORDER ||--o{ ORDER_ITEM : contains
    ORDER {
        uuid orderId PK "訂單唯一識別碼"
        string shippingAddress "收件地址"
        string paymentIdempotencyKey "支付冪等鍵"
        enum status "訂單狀態"
        timestamp createdAt "建立時間"
    }
    ORDER_ITEM {
        string skuCode "商品 SKU 代碼"
        int quantity "數量"
        decimal unitPrice "單價"
    }

    INVENTORY_RESERVATION {
        string skuCode PK "商品 SKU"
        int quantity "預留數量"
        boolean reserved "是否預留成功"
        int remainingQty "剩餘庫存"
    }

    PAYMENT_TRANSACTION {
        uuid transactionId PK "交易編號"
        uuid orderId FK "訂單編號"
        decimal amount "金額"
        string currency "幣別"
        enum status "支付狀態"
    }

    SHIPMENT {
        string trackingNumber PK "物流單號"
        uuid orderId FK "訂單編號"
        string address "收件地址"
        enum status "物流狀態"
    }

    ORDER ||--o| PAYMENT_TRANSACTION : pays
    ORDER ||--o| SHIPMENT : ships
```

### 訂單狀態機

```mermaid
stateDiagram-v2
    [*] --> PENDING: 建立訂單

    PENDING --> INVENTORY_RESERVED: 庫存預留成功
    PENDING --> FAILED: 庫存預留失敗

    INVENTORY_RESERVED --> PAYMENT_COMPLETED: 支付成功
    INVENTORY_RESERVED --> FAILED: 支付失敗

    PAYMENT_COMPLETED --> SHIPPING_REQUESTED: 物流建單
    PAYMENT_COMPLETED --> FAILED: 系統錯誤

    SHIPPING_REQUESTED --> COMPLETED: 流程完成
    SHIPPING_REQUESTED --> FAILED: 系統錯誤

    COMPLETED --> [*]
    FAILED --> [*]
```

---

## 類別圖

### Domain Layer

```mermaid
classDiagram
    class Order {
        -OrderId orderId
        -List~OrderItem~ items
        -String shippingAddress
        -String paymentIdempotencyKey
        -Instant createdAt
        -OrderStatus status
        +create(items, address) Order
        +getTotalAmount() Money
        +markInventoryReserved()
        +markPaymentCompleted()
        +markShippingRequested()
        +markCompleted()
        +markFailed()
    }

    class OrderItem {
        -SkuCode skuCode
        -int quantity
        -Money unitPrice
        +of(skuCode, quantity, unitPrice) OrderItem
        +getSubtotal() Money
    }

    class OrderId {
        -String value
        +of(value) OrderId
        +generate() OrderId
    }

    class SkuCode {
        -String value
        +of(value) SkuCode
    }

    class Money {
        -BigDecimal amount
        -String currency
        +of(amount) Money
        +of(amount, currency) Money
        +add(other) Money
        +multiply(quantity) Money
    }

    class OrderStatus {
        <<enumeration>>
        PENDING
        INVENTORY_RESERVED
        PAYMENT_COMPLETED
        SHIPPING_REQUESTED
        COMPLETED
        FAILED
    }

    Order *-- OrderItem : contains
    Order *-- OrderId : has
    Order *-- OrderStatus : has
    OrderItem *-- SkuCode : has
    OrderItem *-- Money : unitPrice
    Order ..> Money : totalAmount
```

### Application Layer

```mermaid
classDiagram
    class CreateOrderUseCase {
        <<interface>>
        +createOrder(command) CompletableFuture~OrderResult~
    }

    class OrderService {
        -InventoryPort inventoryPort
        -PaymentPort paymentPort
        -ShippingPort shippingPort
        +createOrder(command) CompletableFuture~OrderResult~
        -reserveInventory(order) CompletableFuture~Order~
        -processPayment(order) CompletableFuture~Order~
        -createShipment(order) CompletableFuture~OrderWithShipping~
    }

    class InventoryPort {
        <<interface>>
        +reserveInventory(skuCode, qty) CompletableFuture~InventoryReservationResult~
    }

    class PaymentPort {
        <<interface>>
        +processPayment(orderId, amount, key) CompletableFuture~PaymentResult~
    }

    class ShippingPort {
        <<interface>>
        +createShipment(orderId, address, items) CompletableFuture~ShippingResult~
    }

    CreateOrderUseCase <|.. OrderService : implements
    OrderService --> InventoryPort : uses
    OrderService --> PaymentPort : uses
    OrderService --> ShippingPort : uses
```

### Infrastructure Layer - Adapters

```mermaid
classDiagram
    class InventoryServiceAdapter {
        -WebClient webClient
        -InventoryMapper mapper
        +reserveInventory(skuCode, qty)
        -reserveInventoryFallback()
    }

    class PaymentServiceAdapter {
        -WebClient webClient
        -PaymentMapper mapper
        +processPayment(orderId, amount, key)
        -processPaymentFallback()
    }

    class ShippingServiceAdapter {
        -WebClient webClient
        -ShippingMapper mapper
        +createShipment(orderId, address, items)
        -createShipmentTimeoutFallback()
    }

    class InventoryPort {
        <<interface>>
    }

    class PaymentPort {
        <<interface>>
    }

    class ShippingPort {
        <<interface>>
    }

    InventoryPort <|.. InventoryServiceAdapter : implements
    PaymentPort <|.. PaymentServiceAdapter : implements
    ShippingPort <|.. ShippingServiceAdapter : implements

    note for InventoryServiceAdapter "@Retry(name='inventoryRetry')"
    note for PaymentServiceAdapter "@CircuitBreaker + @Retry"
    note for ShippingServiceAdapter "@TimeLimiter + @CircuitBreaker + @Retry"
```

---

## 循序圖

### 正常流程

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant Controller as OrderController
    participant Service as OrderService
    participant Inventory as InventoryAdapter
    participant Payment as PaymentAdapter
    participant Shipping as ShippingAdapter

    Client->>+Controller: POST /api/orders
    Controller->>+Service: createOrder(command)

    Service->>Service: Order.create(items, address)

    rect rgb(200, 230, 200)
        Note over Service,Inventory: 1. 庫存預留 (Retry)
        Service->>+Inventory: reserveInventory(sku, qty)
        Inventory-->>-Service: InventoryReservationResult
        Service->>Service: order.markInventoryReserved()
    end

    rect rgb(200, 200, 230)
        Note over Service,Payment: 2. 支付處理 (CircuitBreaker)
        Service->>+Payment: processPayment(orderId, amount, key)
        Payment-->>-Service: PaymentResult
        Service->>Service: order.markPaymentCompleted()
    end

    rect rgb(230, 200, 200)
        Note over Service,Shipping: 3. 物流建單 (TimeLimiter)
        Service->>+Shipping: createShipment(orderId, address, items)
        Shipping-->>-Service: ShippingResult
        Service->>Service: order.markCompleted()
    end

    Service-->>-Controller: OrderResult
    Controller-->>-Client: 201 Created
```

### Retry 機制 - 暫時性故障恢復

```mermaid
sequenceDiagram
    autonumber
    participant Service as OrderService
    participant Adapter as InventoryAdapter
    participant R4J as "@Retry"
    participant External as Inventory Service

    Service->>+Adapter: reserveInventory(sku, qty)
    Adapter->>+R4J: decorated call

    R4J->>+External: HTTP POST /api/inventory/deduct
    External-->>-R4J: 503 Service Unavailable

    Note over R4J: 等待 500ms (指數退避)

    R4J->>+External: HTTP POST (重試 1)
    External-->>-R4J: 503 Service Unavailable

    Note over R4J: 等待 1000ms

    R4J->>+External: HTTP POST (重試 2)
    External-->>-R4J: 200 OK

    R4J-->>-Adapter: InventoryResponse
    Adapter-->>-Service: InventoryReservationResult ✓
```

### CircuitBreaker 機制 - 快速失敗保護

```mermaid
sequenceDiagram
    autonumber
    participant Service as OrderService
    participant Adapter as PaymentAdapter
    participant CB as "@CircuitBreaker"
    participant External as Payment Gateway

    Note over CB: 狀態: CLOSED

    loop 連續失敗 (≥5次, 失敗率 ≥50%)
        Service->>+Adapter: processPayment()
        Adapter->>+CB: call
        CB->>+External: HTTP POST
        External-->>-CB: 500 Error
        CB->>CB: 記錄失敗
        CB-->>-Adapter: Exception
        Adapter-->>-Service: fallback response
    end

    Note over CB: 狀態: OPEN

    Service->>+Adapter: processPayment()
    Adapter->>+CB: call
    CB-->>-Adapter: CallNotPermittedException
    Note over CB: 快速失敗 (<50ms)
    Adapter-->>-Service: fallback: "支付服務暫時不可用"

    Note over CB: 等待 30 秒後

    Note over CB: 狀態: HALF_OPEN

    Service->>+Adapter: processPayment()
    Adapter->>+CB: call (探測請求)
    CB->>+External: HTTP POST
    External-->>-CB: 200 OK
    CB->>CB: 記錄成功
    CB-->>-Adapter: PaymentResponse
    Adapter-->>-Service: PaymentResult ✓

    Note over CB: 狀態: CLOSED
```

### TimeLimiter 機制 - 超時降級

```mermaid
sequenceDiagram
    autonumber
    participant Service as OrderService
    participant Adapter as ShippingAdapter
    participant TL as "@TimeLimiter"
    participant External as Shipping Service

    Service->>+Adapter: createShipment()
    Adapter->>+TL: decorated call
    TL->>+External: HTTP POST /api/shipping/create

    Note over TL,External: 服務回應緩慢...

    TL--xExternal: timeout after 3s
    Note over TL: TimeoutException

    TL-->>-Adapter: TimeoutException
    Adapter->>Adapter: shippingTimeLimitFallback()
    Adapter-->>-Service: ShippingResult(DEFERRED)

    Note over Service: 訂單仍成功<br/>物流單號稍後通知
```

---

## API 清單

### Order API

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| POST | `/api/orders` | 建立訂單 | CreateOrderRequest | CreateOrderResponse |

### Actuator Endpoints

| Endpoint | Description |
|----------|-------------|
| GET `/actuator/health` | 健康檢查 |
| GET `/actuator/circuitbreakers` | 斷路器狀態 |
| GET `/actuator/retries` | 重試配置 |
| GET `/actuator/timelimiters` | 超時控制配置 |
| GET `/actuator/prometheus` | Prometheus 指標 |
| GET `/actuator/metrics` | 應用程式指標 |

### Request/Response 範例

**Request:**
```json
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
```

**Response (Success):**
```json
{
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "totalAmount": 3000.00,
  "currency": "TWD",
  "trackingNumber": "TRK123456789",
  "message": "Order created successfully",
  "createdAt": "2026-02-02T12:00:00Z"
}
```

**Response (Deferred Shipping):**
```json
{
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "totalAmount": 3000.00,
  "currency": "TWD",
  "trackingNumber": null,
  "message": "Order created. Tracking number will be provided later via notification.",
  "createdAt": "2026-02-02T12:00:00Z"
}
```

---

## Resilience4j 配置

### Retry 配置

| 實例 | maxAttempts | waitDuration | backoffMultiplier | 適用場景 |
|------|-------------|--------------|-------------------|----------|
| inventoryRetry | 3 | 500ms | 2.0 | 庫存服務暫時性錯誤 |
| paymentRetry | 3 | 1000ms | 2.0 | 支付閘道暫時性錯誤 |
| shippingRetry | 2 | 500ms | - | 物流服務暫時性錯誤 |

### CircuitBreaker 配置

| 實例 | failureRateThreshold | slowCallRateThreshold | waitDurationInOpenState | 適用場景 |
|------|---------------------|----------------------|------------------------|----------|
| inventoryCB | 60% | 80% | 30s | 庫存服務保護 |
| paymentCB | 50% | 80% | 30s | 支付閘道保護（較敏感）|
| shippingCB | 60% | 80% | 30s | 物流服務保護 |

### TimeLimiter 配置

| 實例 | timeoutDuration | cancelRunningFuture | 適用場景 |
|------|-----------------|---------------------|----------|
| inventoryTL | 4s | true | 涵蓋完整重試週期 |
| paymentTL | 8s | true | 涵蓋完整重試週期 |
| shippingTL | 3s | true | 快速降級處理 |

### 裝飾器執行順序

```
TimeLimiter → CircuitBreaker → Retry → HTTP Call
```

---

## 測試案例

### 測試統計

```
Total Tests: 36
├── Unit Tests: 15
│   └── OrderTest (Domain Layer)
└── Integration Tests: 21
    ├── RetryIntegrationTest: 5
    ├── CircuitBreakerIntegrationTest: 6
    ├── TimeLimiterIntegrationTest: 5
    └── CombinedResilienceTest: 5
```

### 測試場景對照表

| 測試類別 | 測試案例 | 驗證目標 |
|----------|----------|----------|
| **RetryIntegrationTest** | | |
| | should_retry_and_succeed_on_transient_failure | 暫時性故障自動重試成功 |
| | should_not_retry_on_4xx_business_error | 4xx 錯誤不重試 |
| | should_not_retry_on_409_insufficient_stock | 庫存不足不重試 |
| | should_return_error_when_retry_exhausted | 重試耗盡返回錯誤 |
| | should_use_exponential_backoff | 指數退避策略 |
| **CircuitBreakerIntegrationTest** | | |
| | should_open_when_failure_rate_exceeds_threshold | 失敗率超標開啟斷路器 |
| | should_fast_fail_when_open | 開啟時快速失敗 (<100ms) |
| | should_return_proper_error_message | 返回適當錯誤訊息 |
| | should_transition_to_half_open | 等待後進入半開狀態 |
| | should_close_when_probe_succeeds | 探測成功時關閉 |
| | should_succeed_when_healthy | 服務正常時成功 |
| **TimeLimiterIntegrationTest** | | |
| | should_timeout_and_fallback_on_slow_response | 慢回應超時降級 |
| | should_return_deferred_message | 返回延後處理訊息 |
| | should_record_timeout_as_failure | 超時計入失敗統計 |
| | should_succeed_when_quick_response | 快速回應時成功 |
| | should_cancel_running_future | 超時時取消請求 |
| **CombinedResilienceTest** | | |
| | should_operate_independently | 各服務斷路器獨立運作 |
| | should_skip_retry_when_cb_open | 斷路器開啟跳過重試 |
| | should_preserve_idempotency_key | 冪等鍵一致性 |
| | should_handle_combined_failures | 組合故障場景 |
| | should_record_metrics | 記錄韌性事件指標 |

### 執行測試

```bash
# 執行所有測試
./gradlew test

# 執行特定測試類別
./gradlew test --tests "RetryIntegrationTest"
./gradlew test --tests "CircuitBreakerIntegrationTest"
./gradlew test --tests "TimeLimiterIntegrationTest"
./gradlew test --tests "CombinedResilienceTest"

# 執行單元測試
./gradlew test --tests "OrderTest"
```

---

## 專案結構

```
src/
├── main/java/com/example/order/
│   ├── domain/                          # 領域層 (純業務邏輯)
│   │   ├── model/
│   │   │   ├── Order.java              # 聚合根
│   │   │   ├── OrderItem.java          # 實體
│   │   │   ├── OrderId.java            # 值物件
│   │   │   ├── Money.java              # 值物件
│   │   │   ├── SkuCode.java            # 值物件
│   │   │   └── OrderStatus.java        # 列舉
│   │   └── exception/
│   │       ├── DomainException.java
│   │       └── InsufficientStockException.java
│   │
│   ├── application/                     # 應用層 (用例編排)
│   │   ├── port/
│   │   │   ├── in/
│   │   │   │   └── CreateOrderUseCase.java
│   │   │   └── out/
│   │   │       ├── InventoryPort.java
│   │   │       ├── PaymentPort.java
│   │   │       └── ShippingPort.java
│   │   ├── service/
│   │   │   └── OrderService.java
│   │   └── dto/
│   │       ├── CreateOrderCommand.java
│   │       └── OrderResult.java
│   │
│   └── infrastructure/                  # 基礎設施層 (框架整合)
│       ├── adapter/
│       │   ├── in/web/
│       │   │   ├── OrderController.java
│       │   │   ├── dto/
│       │   │   └── mapper/
│       │   └── out/
│       │       ├── inventory/
│       │       ├── payment/
│       │       └── shipping/
│       ├── config/
│       │   ├── Resilience4jEventConfig.java
│       │   ├── WebClientConfig.java
│       │   └── OpenApiConfig.java
│       └── exception/
│           ├── GlobalExceptionHandler.java
│           ├── RetryableServiceException.java
│           ├── NonRetryableServiceException.java
│           ├── BusinessException.java
│           └── ServiceUnavailableException.java
│
└── test/java/com/example/order/
    ├── integration/
    │   ├── RetryIntegrationTest.java
    │   ├── CircuitBreakerIntegrationTest.java
    │   ├── TimeLimiterIntegrationTest.java
    │   └── CombinedResilienceTest.java
    ├── unit/domain/
    │   └── OrderTest.java
    └── support/
        └── WireMockTestSupport.java
```

---

## 技術棧

| 類別 | 技術 | 版本 |
|------|------|------|
| Language | Java | 17+ |
| Framework | Spring Boot | 3.2.x |
| Reactive | Spring WebFlux | 6.1.x |
| Resilience | Resilience4j | 2.2.x |
| API Documentation | SpringDoc OpenAPI | 2.3.x |
| Metrics | Micrometer + Prometheus | 1.12.x |
| Testing | JUnit 5 + WireMock | 5.10 / 3.3.x |
| Build | Gradle | 8.5 |

---

## 學習資源

### 相關概念

- [Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture/)
- [Domain-Driven Design](https://martinfowler.com/bliki/DomainDrivenDesign.html)
- [Circuit Breaker Pattern](https://martinfowler.com/bliki/CircuitBreaker.html)
- [Retry Pattern](https://docs.microsoft.com/en-us/azure/architecture/patterns/retry)

### Resilience4j 文件

- [Resilience4j Documentation](https://resilience4j.readme.io/)
- [Resilience4j with Spring Boot](https://resilience4j.readme.io/docs/getting-started-3)

### Spring Boot

- [Spring WebFlux](https://docs.spring.io/spring-framework/reference/web/webflux.html)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)

---

## License

Apache 2.0
