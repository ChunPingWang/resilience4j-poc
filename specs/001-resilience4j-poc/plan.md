# Implementation Plan: Resilience4j 韌性機制 PoC

**Branch**: `001-resilience4j-poc` | **Date**: 2026-02-02 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-resilience4j-poc/spec.md`

## Summary

建立訂單服務（Order Service）的韌性保護機制，透過 Resilience4j 實現對三個下游服務（庫存、支付、物流）的 Retry、CircuitBreaker、TimeLimiter 三層防護。採用六角形架構，將韌性機制封裝於 Infrastructure 層的 Adapter 中，確保 Domain 與 Application 層保持純淨無框架依賴。

## Technical Context

**Language/Version**: Java 17+
**Primary Dependencies**: Spring Boot 3.2.x, Resilience4j 2.2.x, WebClient, Micrometer
**Storage**: N/A（PoC 階段不涉及持久化）
**Testing**: JUnit 5 + AssertJ + WireMock 3.x
**Target Platform**: Linux/macOS server (JVM)
**Project Type**: Single project (backend service)
**Performance Goals**: 快速失敗 P99 ≤ 50ms，正常請求 P95 ≤ 3s
**Constraints**: Resilience4j overhead ≤ 5ms/call，下游故障時資源使用率 ≤ 70%
**Scale/Scope**: PoC 驗證，模擬 30% 故障率環境

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evidence |
|-----------|--------|----------|
| I. Code Quality | ✅ PASS | 自定義例外體系、結構化日誌、Javadoc 規劃 |
| II. TDD | ✅ PASS | WireMock 故障場景測試先於實作，Given-When-Then 格式 |
| III. BDD | ✅ PASS | Spec 已定義 18 項驗收場景，採用中文 BDD 語法 |
| IV. DDD | ✅ PASS | Domain 層含 Order/OrderItem/OrderStatus，使用統一語言 |
| V. SOLID | ✅ PASS | Port/Adapter 分離（DIP），每個 Adapter 單一職責（SRP） |
| VI. Hexagonal Architecture | ✅ PASS | 三層結構：Domain → Application → Infrastructure |
| VII. Dependency Inversion | ✅ PASS | 框架註解僅在 Infrastructure，Port 介面在 Application |

**Gate Result**: ✅ All principles satisfied. Proceed to Phase 0.

## Project Structure

### Documentation (this feature)

```text
specs/001-resilience4j-poc/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── order-api.yaml   # Order Service OpenAPI
│   ├── inventory-api.yaml
│   ├── payment-api.yaml
│   └── shipping-api.yaml
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
src/main/java/com/example/order/
├── domain/                          # Innermost: pure business logic
│   ├── model/
│   │   ├── Order.java
│   │   ├── OrderId.java            # Value Object
│   │   ├── OrderItem.java
│   │   ├── OrderStatus.java        # Enum
│   │   ├── Money.java              # Value Object
│   │   └── SkuCode.java            # Value Object
│   └── exception/
│       ├── DomainException.java
│       └── InsufficientStockException.java
├── application/                     # Middle: orchestration
│   ├── port/
│   │   ├── in/
│   │   │   └── CreateOrderUseCase.java
│   │   └── out/
│   │       ├── InventoryPort.java
│   │       ├── PaymentPort.java
│   │       └── ShippingPort.java
│   ├── service/
│   │   └── OrderService.java       # Implements CreateOrderUseCase
│   └── dto/
│       ├── CreateOrderCommand.java
│       └── OrderResult.java
└── infrastructure/                  # Outermost: frameworks
    ├── adapter/
    │   ├── in/
    │   │   └── web/
    │   │       ├── OrderController.java
    │   │       ├── dto/
    │   │       │   ├── CreateOrderRequest.java
    │   │       │   └── CreateOrderResponse.java
    │   │       └── mapper/
    │   │           └── OrderWebMapper.java
    │   └── out/
    │       ├── inventory/
    │       │   ├── InventoryServiceAdapter.java
    │       │   ├── dto/
    │       │   │   ├── InventoryRequest.java
    │       │   │   └── InventoryResponse.java
    │       │   └── mapper/
    │       │       └── InventoryMapper.java
    │       ├── payment/
    │       │   ├── PaymentServiceAdapter.java
    │       │   ├── dto/
    │       │   │   ├── PaymentRequest.java
    │       │   │   └── PaymentResponse.java
    │       │   └── mapper/
    │       │       └── PaymentMapper.java
    │       └── shipping/
    │           ├── ShippingServiceAdapter.java
    │           ├── dto/
    │           │   ├── ShippingRequest.java
    │           │   └── ShippingResponse.java
    │           └── mapper/
    │               └── ShippingMapper.java
    ├── config/
    │   ├── Resilience4jConfig.java
    │   ├── Resilience4jEventConfig.java
    │   └── WebClientConfig.java
    └── exception/
        ├── RetryableServiceException.java
        ├── NonRetryableServiceException.java
        ├── BusinessException.java
        ├── ServiceUnavailableException.java
        └── GlobalExceptionHandler.java

src/main/resources/
├── application.yml                  # Main config with Resilience4j
└── application-test.yml             # Test profile

src/test/java/com/example/order/
├── integration/                     # BDD-style integration tests
│   ├── RetryIntegrationTest.java
│   ├── CircuitBreakerIntegrationTest.java
│   ├── TimeLimiterIntegrationTest.java
│   └── CombinedResilienceTest.java
├── unit/
│   ├── domain/
│   │   └── OrderTest.java
│   └── application/
│       └── OrderServiceTest.java
└── support/
    └── WireMockTestSupport.java
```

**Structure Decision**: Single Spring Boot project following Hexagonal Architecture. Domain layer contains pure business entities. Application layer defines ports (interfaces) for external dependencies. Infrastructure layer implements adapters with Resilience4j decorators. All framework annotations are confined to infrastructure layer.

## Complexity Tracking

> **No violations detected.** The design adheres to all constitution principles.

| Aspect | Decision | Justification |
|--------|----------|---------------|
| Three adapter packages | Separate adapters per downstream service | ISP compliance: each adapter has single responsibility |
| Mapper in infrastructure | Mappers reside in adapter packages | Constitution requires explicit layer boundary mapping |
| Exception hierarchy | Four-level exception structure | Clear separation: retryable vs non-retryable vs business vs unavailable |
