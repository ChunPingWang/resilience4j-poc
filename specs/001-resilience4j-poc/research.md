# Research: Resilience4j 韌性機制 PoC

**Date**: 2026-02-02
**Source**: TECH.md（技術需求計劃書）

## Research Summary

技術決策已在 TECH.md 中完整定義，無需額外研究。本文件記錄關鍵決策及其理由。

---

## 1. Resilience4j 裝飾器執行順序

**Decision**: TimeLimiter → CircuitBreaker → Retry → Actual HTTP Call

**Rationale**:
- TimeLimiter 在最外層：確保無論重試幾次，整體耗時都不超過上限
- CircuitBreaker 在 Retry 外層：斷路器開啟時直接拒絕，不進入重試迴圈
- Retry 在最內層：每次重試的結果都會被 CircuitBreaker 記錄

**Alternatives Considered**:
- Retry 在最外層：會導致斷路器開啟後仍進行無效重試
- CircuitBreaker 在 TimeLimiter 外層：無法正確控制整體超時

---

## 2. HTTP 客戶端選擇

**Decision**: Spring WebClient (WebFlux)

**Rationale**:
- 非阻塞：適合與 Resilience4j 的 CompletableFuture 整合
- 原生支援 Reactor：與 resilience4j-reactor 模組無縫整合
- Spring Boot 3.x 推薦：取代 RestTemplate

**Alternatives Considered**:
- RestTemplate：阻塞式，不適合高並發場景
- Apache HttpClient：需額外封裝，整合較複雜
- OkHttp：可行但缺乏 Spring 生態整合

---

## 3. 重試策略設計

**Decision**: 指數退避 + 服務差異化配置

| Service | maxAttempts | baseWait | multiplier | maxWait |
|---------|-------------|----------|------------|---------|
| Inventory | 3 | 500ms | 2.0 | 4s |
| Payment | 3 | 1000ms | 2.0 | 8s |
| Shipping | 2 | 500ms | N/A | N/A |

**Rationale**:
- 庫存服務：快速重試，業務容忍度低
- 支付閘道：較長間隔，外部服務恢復需時間
- 物流服務：僅兩次重試，超時後走降級策略

**Alternatives Considered**:
- 固定間隔重試：可能在下游過載時加劇問題
- 隨機抖動（jitter）：PoC 階段暫不採用，生產環境可考慮

---

## 4. 斷路器配置策略

**Decision**: COUNT_BASED 滑動窗口 + 服務差異化閾值

| Service | windowSize | failureThreshold | slowCallThreshold | slowCallDuration |
|---------|------------|------------------|-------------------|------------------|
| Inventory | 10 | 60% | 80% | 2s |
| Payment | 10 | 50% | 80% | 3s |
| Shipping | 10 | 60% | 80% | 2s |

**Rationale**:
- 支付閘道閾值較低（50%）：資金相關，需更敏感保護
- 慢呼叫納入統計：服務壓力大時可提前介入
- 窗口大小 10：足夠統計顯著性，又不會太慢反應

**Alternatives Considered**:
- TIME_BASED 窗口：流量變化大時可能誤判
- 更大窗口（如 100）：反應太慢，不適合 PoC 驗證

---

## 5. 超時配置設計

**Decision**: 依據重試總時間計算各服務超時

| Service | Timeout | Calculation |
|---------|---------|-------------|
| Inventory | 4s | 3 retries × (500ms + 1s + 2s) + buffer |
| Payment | 8s | 3 retries × (1s + 2s + 4s) + buffer |
| Shipping | 3s | 2 retries × 500ms + graceful degradation |

**Rationale**:
- 超時需涵蓋完整重試週期，否則重試無意義
- 物流服務超時較短：業務允許延後處理

**Alternatives Considered**:
- 單次呼叫超時 + 重試獨立計時：配置複雜，行為難預測
- 統一超時值：無法適應不同服務特性

---

## 6. 例外處理策略

**Decision**: 四層例外體系

```
Exception
├── RetryableServiceException     ← 5xx / 網路錯誤
├── NonRetryableServiceException  ← 4xx 非業務錯誤
├── BusinessException             ← 業務邏輯錯誤
└── ServiceUnavailableException   ← Fallback 最終拋出
```

**Rationale**:
- 清楚區分哪些錯誤應觸發重試/斷路器
- BusinessException 不計入失敗率，避免誤開斷路器
- ServiceUnavailableException 統一處理所有降級回應

**Alternatives Considered**:
- 使用 HTTP 狀態碼直接判斷：缺乏語意清晰度
- 單一異常類型 + 錯誤碼：失去類型安全

---

## 7. 測試策略

**Decision**: WireMock 模擬 + Scenario 狀態機

**Rationale**:
- WireMock Scenario：可模擬「前 N 次失敗、後續成功」的複雜場景
- 獨立於真實下游服務：PoC 可完全自主執行
- 支援延遲注入：驗證 TimeLimiter 行為

**Test Coverage**:
- 10 個故障場景（F-01 ~ F-10）
- 覆蓋 Retry、CircuitBreaker、TimeLimiter 獨立與組合行為

---

## 8. 可觀測性設計

**Decision**: Micrometer + Actuator + 結構化日誌

**Metrics Exposed**:
- `resilience4j_retry_calls_total`
- `resilience4j_circuitbreaker_state`
- `resilience4j_circuitbreaker_failure_rate`
- `resilience4j_timelimiter_calls_total`

**Log Format**:
```
[RETRY] name=inventoryRetry, attempt=2, waitDuration=1000ms, cause=...
[CB_STATE] name=paymentCB, from=CLOSED, to=OPEN
[TIMEOUT] name=shippingTL, duration=3000ms
```

**Rationale**:
- Prometheus 格式：業界標準，易於 Grafana 整合
- 結構化日誌：便於日誌分析與告警

---

## Unresolved Questions

None. All technical decisions are fully specified in TECH.md.

## Next Steps

Proceed to Phase 1: Generate data-model.md, contracts/, quickstart.md
