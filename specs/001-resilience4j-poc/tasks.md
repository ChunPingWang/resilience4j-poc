# Tasks: Resilience4j 韌性機制 PoC

**Input**: Design documents from `/specs/001-resilience4j-poc/`
**Prerequisites**: plan.md, spec.md, data-model.md, contracts/, research.md, quickstart.md

**Tests**: TDD is REQUIRED per Constitution (Principle II). Tests MUST be written FIRST and FAIL before implementation.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- Include exact file paths in descriptions

## Path Conventions

Base path: `src/main/java/com/example/order/`
Test path: `src/test/java/com/example/order/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [ ] T001 Initialize Spring Boot 3.2.x project with Gradle in build.gradle
- [ ] T002 [P] Add Resilience4j dependencies (resilience4j-spring-boot3, resilience4j-reactor, resilience4j-micrometer) in build.gradle
- [ ] T003 [P] Add WireMock test dependency (wiremock-standalone:3.3.1) in build.gradle
- [ ] T004 [P] Add Lombok and Micrometer Prometheus dependencies in build.gradle
- [ ] T005 Create base package structure per plan.md (domain/, application/, infrastructure/)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Domain Layer (No Framework Dependencies)

- [ ] T006 [P] Create OrderId value object in domain/model/OrderId.java
- [ ] T007 [P] Create SkuCode value object in domain/model/SkuCode.java
- [ ] T008 [P] Create Money value object in domain/model/Money.java
- [ ] T009 [P] Create OrderStatus enum in domain/model/OrderStatus.java
- [ ] T010 [P] Create OrderItem entity in domain/model/OrderItem.java
- [ ] T011 Create Order aggregate root in domain/model/Order.java (depends on T006-T010)
- [ ] T012 [P] Create DomainException base class in domain/exception/DomainException.java
- [ ] T013 [P] Create InsufficientStockException in domain/exception/InsufficientStockException.java

### Application Layer (Ports & Use Cases)

- [ ] T014 [P] Create InventoryPort interface in application/port/out/InventoryPort.java
- [ ] T015 [P] Create PaymentPort interface in application/port/out/PaymentPort.java
- [ ] T016 [P] Create ShippingPort interface in application/port/out/ShippingPort.java
- [ ] T017 Create CreateOrderUseCase interface in application/port/in/CreateOrderUseCase.java
- [ ] T018 [P] Create CreateOrderCommand DTO in application/dto/CreateOrderCommand.java
- [ ] T019 [P] Create OrderResult DTO in application/dto/OrderResult.java

### Infrastructure Layer (Exceptions & Config)

- [ ] T020 [P] Create RetryableServiceException in infrastructure/exception/RetryableServiceException.java
- [ ] T021 [P] Create NonRetryableServiceException in infrastructure/exception/NonRetryableServiceException.java
- [ ] T022 [P] Create BusinessException in infrastructure/exception/BusinessException.java
- [ ] T023 [P] Create ServiceUnavailableException in infrastructure/exception/ServiceUnavailableException.java
- [ ] T024 Create GlobalExceptionHandler in infrastructure/exception/GlobalExceptionHandler.java
- [ ] T025 Create WebClientConfig in infrastructure/config/WebClientConfig.java
- [ ] T026 Create base application.yml with Actuator and logging config in src/main/resources/application.yml

### Test Infrastructure

- [ ] T027 Create WireMockTestSupport base class in src/test/java/com/example/order/support/WireMockTestSupport.java
- [ ] T028 Create application-test.yml with test profile in src/test/resources/application-test.yml

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - 暫時性故障自動復原 (Priority: P1) 🎯 MVP

**Goal**: 實現 Retry 機制，對庫存服務的暫時性錯誤（5xx、網路錯誤）自動重試，業務錯誤（4xx）不重試

**Independent Test**: 模擬庫存服務返回 503 錯誤，驗證系統自動重試後成功完成扣減

### Tests for User Story 1 (TDD - Write FIRST, Ensure FAIL)

- [ ] T029 [P] [US1] Write RetryIntegrationTest in src/test/java/com/example/order/integration/RetryIntegrationTest.java
  - Test: should_retry_and_succeed_on_transient_inventory_failure (F-01)
  - Test: should_not_retry_on_4xx_business_error (F-02)
  - Test: should_not_retry_on_409_insufficient_stock (F-03)
  - Test: should_return_error_message_when_retry_exhausted
  - Test: should_use_exponential_backoff_between_retries

- [ ] T030 [P] [US1] Write OrderTest unit test in src/test/java/com/example/order/unit/domain/OrderTest.java
  - Test: should_create_order_with_valid_items
  - Test: should_calculate_total_amount_correctly
  - Test: should_reject_empty_items_list

### Implementation for User Story 1

- [ ] T031 [P] [US1] Create InventoryRequest DTO in infrastructure/adapter/out/inventory/dto/InventoryRequest.java
- [ ] T032 [P] [US1] Create InventoryResponse DTO in infrastructure/adapter/out/inventory/dto/InventoryResponse.java
- [ ] T033 [US1] Create InventoryMapper in infrastructure/adapter/out/inventory/mapper/InventoryMapper.java
- [ ] T034 [US1] Add Retry configuration for inventoryRetry in src/main/resources/application.yml
- [ ] T035 [US1] Create InventoryServiceAdapter with @Retry in infrastructure/adapter/out/inventory/InventoryServiceAdapter.java
  - Implement InventoryPort interface
  - Apply @Retry(name = "inventoryRetry", fallbackMethod = "...")
  - Map 5xx to RetryableServiceException, 4xx to NonRetryableServiceException
  - Add fallback method for retry exhausted

- [ ] T036 [US1] Implement OrderService with inventory integration in application/service/OrderService.java
- [ ] T037 [US1] Add Resilience4j Retry event logging in infrastructure/config/Resilience4jEventConfig.java (retry events only)

**Checkpoint**: US1 完成 - Retry 機制可獨立驗證，模擬 503 錯誤後自動重試成功

---

## Phase 4: User Story 2 - 支付閘道快速失敗保護 (Priority: P2)

**Goal**: 實現 CircuitBreaker 機制，當支付閘道持續故障時快速失敗保護上游資源

**Independent Test**: 連續發送失敗請求使斷路器開啟，驗證後續請求在 50ms 內快速失敗

### Tests for User Story 2 (TDD - Write FIRST, Ensure FAIL)

- [ ] T038 [P] [US2] Write CircuitBreakerIntegrationTest in src/test/java/com/example/order/integration/CircuitBreakerIntegrationTest.java
  - Test: should_open_circuit_breaker_when_failure_rate_exceeds_threshold (F-04)
  - Test: should_transition_to_half_open_after_wait_duration (F-05)
  - Test: should_fast_fail_when_circuit_breaker_open (F-06)
  - Test: should_close_circuit_breaker_when_probe_succeeds
  - Test: should_return_proper_error_message_on_circuit_open

### Implementation for User Story 2

- [ ] T039 [P] [US2] Create PaymentRequest DTO in infrastructure/adapter/out/payment/dto/PaymentRequest.java
- [ ] T040 [P] [US2] Create PaymentResponse DTO in infrastructure/adapter/out/payment/dto/PaymentResponse.java
- [ ] T041 [US2] Create PaymentMapper in infrastructure/adapter/out/payment/mapper/PaymentMapper.java
- [ ] T042 [US2] Add CircuitBreaker configuration for paymentCB in src/main/resources/application.yml
  - failureRateThreshold: 50
  - slowCallRateThreshold: 80
  - slowCallDurationThreshold: 3s
  - waitDurationInOpenState: 30s

- [ ] T043 [US2] Create PaymentServiceAdapter with @CircuitBreaker and @Retry in infrastructure/adapter/out/payment/PaymentServiceAdapter.java
  - Implement PaymentPort interface
  - Apply @CircuitBreaker(name = "paymentCB", fallbackMethod = "...")
  - Apply @Retry(name = "paymentRetry")
  - Include idempotency-key header handling
  - Add fallback method for circuit breaker open

- [ ] T044 [US2] Extend OrderService with payment integration in application/service/OrderService.java
- [ ] T045 [US2] Add CircuitBreaker event logging in infrastructure/config/Resilience4jEventConfig.java

**Checkpoint**: US2 完成 - CircuitBreaker 機制可獨立驗證，斷路器開啟後快速失敗

---

## Phase 5: User Story 3 - 物流服務超時保護 (Priority: P3)

**Goal**: 實現 TimeLimiter 機制，對物流服務慢回應設定超時並降級處理

**Independent Test**: 模擬物流服務延遲 5 秒，驗證系統在 3 秒後觸發超時並返回降級回應

### Tests for User Story 3 (TDD - Write FIRST, Ensure FAIL)

- [ ] T046 [P] [US3] Write TimeLimiterIntegrationTest in src/test/java/com/example/order/integration/TimeLimiterIntegrationTest.java
  - Test: should_timeout_and_fallback_on_slow_shipping (F-07)
  - Test: should_record_timeout_as_failure_in_circuit_breaker (F-08)
  - Test: should_return_deferred_message_on_timeout
  - Test: should_cancel_running_future_on_timeout

### Implementation for User Story 3

- [ ] T047 [P] [US3] Create ShippingRequest DTO in infrastructure/adapter/out/shipping/dto/ShippingRequest.java
- [ ] T048 [P] [US3] Create ShippingResponse DTO in infrastructure/adapter/out/shipping/dto/ShippingResponse.java
- [ ] T049 [US3] Create ShippingMapper in infrastructure/adapter/out/shipping/mapper/ShippingMapper.java
- [ ] T050 [US3] Add TimeLimiter configuration for shippingTL in src/main/resources/application.yml
  - timeoutDuration: 3s
  - cancelRunningFuture: true

- [ ] T051 [US3] Create ShippingServiceAdapter with @TimeLimiter, @CircuitBreaker, @Retry in infrastructure/adapter/out/shipping/ShippingServiceAdapter.java
  - Implement ShippingPort interface
  - Apply @TimeLimiter(name = "shippingTL", fallbackMethod = "shippingTimeLimitFallback")
  - Apply @CircuitBreaker(name = "shippingCB")
  - Apply @Retry(name = "shippingRetry")
  - Return ShippingResponse.deferred() in timeout fallback

- [ ] T052 [US3] Extend OrderService with shipping integration in application/service/OrderService.java
- [ ] T053 [US3] Add TimeLimiter event logging in infrastructure/config/Resilience4jEventConfig.java

**Checkpoint**: US3 完成 - TimeLimiter 超時降級可獨立驗證

---

## Phase 6: User Story 4 - 三模式協作完整保護 (Priority: P4)

**Goal**: 驗證 TimeLimiter → CircuitBreaker → Retry 三層裝飾器正確協作

**Independent Test**: 組合故障場景驗證機制執行順序與獨立性

### Tests for User Story 4 (TDD - Write FIRST, Ensure FAIL)

- [ ] T054 [P] [US4] Write CombinedResilienceTest in src/test/java/com/example/order/integration/CombinedResilienceTest.java
  - Test: should_execute_in_correct_order_timelimiter_circuitbreaker_retry
  - Test: should_skip_retry_when_circuit_breaker_open
  - Test: should_retry_on_single_timeout_if_retry_remaining
  - Test: should_log_all_resilience_events (F-09 partial)
  - Test: should_operate_circuit_breakers_independently (F-09)
  - Test: should_preserve_idempotency_key_across_retries (F-10)

### Implementation for User Story 4

- [ ] T055 [US4] Add all remaining Resilience4j configurations in src/main/resources/application.yml
  - inventoryTL (4s), inventoryCB (60% threshold)
  - paymentTL (8s)
  - Ensure all three services have TimeLimiter + CircuitBreaker + Retry

- [ ] T056 [US4] Update InventoryServiceAdapter with @TimeLimiter and @CircuitBreaker in infrastructure/adapter/out/inventory/InventoryServiceAdapter.java
- [ ] T057 [US4] Finalize Resilience4jEventConfig with all event listeners in infrastructure/config/Resilience4jEventConfig.java

**Checkpoint**: US4 完成 - 三層防護整合驗證通過

---

## Phase 7: Web Layer & End-to-End Integration

**Purpose**: Expose REST API and complete order flow

### Tests

- [ ] T058 [P] Write OrderServiceTest unit test in src/test/java/com/example/order/unit/application/OrderServiceTest.java
  - Test: should_create_order_successfully_with_all_services
  - Test: should_handle_inventory_failure_gracefully
  - Test: should_handle_payment_failure_gracefully
  - Test: should_complete_order_with_deferred_shipping

### Implementation

- [ ] T059 [P] Create CreateOrderRequest DTO in infrastructure/adapter/in/web/dto/CreateOrderRequest.java
- [ ] T060 [P] Create CreateOrderResponse DTO in infrastructure/adapter/in/web/dto/CreateOrderResponse.java
- [ ] T061 Create OrderWebMapper in infrastructure/adapter/in/web/mapper/OrderWebMapper.java
- [ ] T062 Create OrderController in infrastructure/adapter/in/web/OrderController.java
  - POST /api/orders → CreateOrderUseCase
  - GET /api/orders/{orderId} (optional)

- [ ] T063 Configure Actuator endpoints in src/main/resources/application.yml
  - Expose: health, metrics, circuitbreakers, retries, timelimiters, prometheus

- [ ] T064 Create OrderApplication main class in OrderApplication.java

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, code quality, final verification

- [ ] T065 [P] Add Javadoc to all public interfaces in application/port/
- [ ] T066 [P] Add Javadoc to domain model classes in domain/model/
- [ ] T067 Verify all tests pass with `./gradlew test`
- [ ] T068 Run application and validate Actuator endpoints per quickstart.md
- [ ] T069 Capture Prometheus metrics output for documentation
- [ ] T070 Create ADR for decorator order decision in docs/adr/001-resilience4j-decorator-order.md

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1 (Setup) ──► Phase 2 (Foundational) ──┬──► Phase 3 (US1: Retry) ──┐
                                              │                           │
                                              ├──► Phase 4 (US2: CB) ─────┼──► Phase 7 (Web Layer)
                                              │                           │
                                              └──► Phase 5 (US3: TL) ─────┘
                                                          │
                                                          ▼
                                              Phase 6 (US4: Combined)
                                                          │
                                                          ▼
                                              Phase 8 (Polish)
```

### User Story Dependencies

- **US1 (P1)**: Can start after Phase 2 - MVP for Retry mechanism
- **US2 (P2)**: Can start after Phase 2 - Adds CircuitBreaker, depends on US1 for payment flow
- **US3 (P3)**: Can start after Phase 2 - Adds TimeLimiter, depends on US1+US2 for full flow
- **US4 (P4)**: Requires US1+US2+US3 complete - Integration verification

### Within Each User Story (TDD Flow)

1. Write tests FIRST → Ensure they FAIL
2. Models/DTOs (parallelizable)
3. Mappers
4. Configuration (application.yml)
5. Adapter implementation (with Resilience4j decorators)
6. Service integration
7. Run tests → Ensure they PASS

---

## Parallel Execution Examples

### Phase 2: Domain Models (All Parallel)

```bash
# Launch all value objects in parallel:
T006: OrderId, T007: SkuCode, T008: Money, T009: OrderStatus, T010: OrderItem
```

### Phase 3: User Story 1

```bash
# Tests (parallel):
T029: RetryIntegrationTest
T030: OrderTest

# DTOs (parallel after tests):
T031: InventoryRequest
T032: InventoryResponse
```

### Phase 4-6: User Stories Can Overlap

```bash
# If multiple developers available:
Developer A: US2 (CircuitBreaker)
Developer B: US3 (TimeLimiter)
# After both complete:
Team: US4 (Combined verification)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1 (Retry)
4. **STOP and VALIDATE**: `./gradlew test --tests "RetryIntegrationTest"`
5. Demo: 模擬 503 錯誤，展示自動重試成功

### Incremental Delivery

| Milestone | User Stories | Key Validation |
|-----------|--------------|----------------|
| MVP | US1 | Retry 自動復原，30% 故障率下成功率 ≥ 80% |
| +CircuitBreaker | US1+US2 | 快速失敗 P99 ≤ 50ms |
| +TimeLimiter | US1+US2+US3 | 超時降級，物流延後處理 |
| Full PoC | All | 三層防護整合，100% 事件可追蹤 |

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps to spec.md user stories (US1=P1, US2=P2, US3=P3, US4=P4)
- TDD is MANDATORY per Constitution Principle II
- Each story should be independently completable and testable
- Commit after each task or logical group
- Run `./gradlew test` frequently to validate progress
- All Resilience4j decorators MUST be in Infrastructure layer per Constitution Principle VI
