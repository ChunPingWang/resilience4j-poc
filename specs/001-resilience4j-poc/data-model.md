# Data Model: Resilience4j 韌性機制 PoC

**Date**: 2026-02-02
**Layer**: Domain

---

## Entity Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         Domain Layer                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐       1:N       ┌─────────────┐               │
│  │   Order     │─────────────────│  OrderItem  │               │
│  │             │                 │             │               │
│  │ - orderId   │                 │ - skuCode   │               │
│  │ - status    │                 │ - quantity  │               │
│  │ - createdAt │                 │ - unitPrice │               │
│  └─────────────┘                 └─────────────┘               │
│         │                                                       │
│         │ uses                                                  │
│         ▼                                                       │
│  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐          │
│  │  OrderId    │   │   Money     │   │  SkuCode    │          │
│  │ (Value Obj) │   │ (Value Obj) │   │ (Value Obj) │          │
│  └─────────────┘   └─────────────┘   └─────────────┘          │
│                                                                 │
│  ┌─────────────┐                                               │
│  │ OrderStatus │   PENDING → INVENTORY_RESERVED →              │
│  │   (Enum)    │   PAYMENT_COMPLETED → SHIPPING_REQUESTED →    │
│  │             │   COMPLETED / FAILED                          │
│  └─────────────┘                                               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Entities

### Order (Aggregate Root)

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| orderId | OrderId | 訂單唯一識別碼 | Required, Immutable |
| items | List&lt;OrderItem&gt; | 訂單商品項目 | Required, Non-empty |
| status | OrderStatus | 訂單狀態 | Required |
| totalAmount | Money | 訂單總金額 | Required, Calculated |
| createdAt | Instant | 建立時間 | Required, Immutable |
| paymentIdempotencyKey | String | 支付冪等鍵 | Required, Immutable, UUID format |

**Business Rules**:
- Order MUST have at least one OrderItem
- totalAmount = Σ(item.quantity × item.unitPrice)
- paymentIdempotencyKey MUST remain constant across retries
- Order can only transition forward in status (no rollback)

**State Transitions**:
```
PENDING
    │
    ▼ (Inventory reserved successfully)
INVENTORY_RESERVED
    │
    ▼ (Payment completed successfully)
PAYMENT_COMPLETED
    │
    ▼ (Shipping requested - may be deferred)
SHIPPING_REQUESTED
    │
    ▼ (All steps completed)
COMPLETED

Any State → FAILED (on unrecoverable error)
```

---

### OrderItem

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| skuCode | SkuCode | 商品 SKU 代碼 | Required |
| quantity | int | 購買數量 | Required, > 0 |
| unitPrice | Money | 單價 | Required, ≥ 0 |

**Business Rules**:
- quantity MUST be positive integer
- unitPrice MUST be non-negative

---

## Value Objects

### OrderId

| Field | Type | Description |
|-------|------|-------------|
| value | String | UUID 格式的訂單編號 |

**Validation**:
- Format: UUID v4
- Example: `550e8400-e29b-41d4-a716-446655440000`

---

### SkuCode

| Field | Type | Description |
|-------|------|-------------|
| value | String | 商品 SKU 代碼 |

**Validation**:
- Pattern: `^[A-Z]{3}[0-9]{3}$`
- Example: `SKU001`, `PRD123`

---

### Money

| Field | Type | Description |
|-------|------|-------------|
| amount | BigDecimal | 金額 |
| currency | String | 幣別（預設 TWD） |

**Validation**:
- amount MUST be ≥ 0
- currency default: `TWD`

**Operations**:
- `add(Money other)`: 相加（需相同幣別）
- `multiply(int quantity)`: 乘以數量

---

### OrderStatus (Enum)

| Value | Description |
|-------|-------------|
| PENDING | 初始狀態，等待處理 |
| INVENTORY_RESERVED | 庫存已預留 |
| PAYMENT_COMPLETED | 支付已完成 |
| SHIPPING_REQUESTED | 物流已請求（可能為 deferred） |
| COMPLETED | 訂單完成 |
| FAILED | 訂單失敗 |

---

## External Service DTOs (Infrastructure Layer)

### Inventory Service

**Request (InventoryRequest)**:
| Field | Type | Description |
|-------|------|-------------|
| skuCode | String | SKU 代碼 |
| quantity | int | 預留數量 |

**Response (InventoryResponse)**:
| Field | Type | Description |
|-------|------|-------------|
| skuCode | String | SKU 代碼 |
| reserved | boolean | 是否預留成功 |
| remainingQty | int | 剩餘庫存 |

---

### Payment Service

**Request (PaymentRequest)**:
| Field | Type | Description |
|-------|------|-------------|
| orderId | String | 訂單編號 |
| amount | BigDecimal | 支付金額 |
| currency | String | 幣別 |
| idempotencyKey | String | 冪等鍵（Header） |

**Response (PaymentResponse)**:
| Field | Type | Description |
|-------|------|-------------|
| transactionId | String | 交易編號 |
| status | String | 支付狀態 (SUCCESS/FAILED) |
| message | String | 訊息 |

---

### Shipping Service

**Request (ShippingRequest)**:
| Field | Type | Description |
|-------|------|-------------|
| orderId | String | 訂單編號 |
| address | String | 收件地址 |
| items | List&lt;ShippingItem&gt; | 商品列表 |

**Response (ShippingResponse)**:
| Field | Type | Description |
|-------|------|-------------|
| trackingNumber | String | 物流單號（可能為空） |
| status | String | 狀態 (CREATED/DEFERRED) |
| message | String | 訊息 |

---

## Port Interfaces (Application Layer)

### InventoryPort

```java
public interface InventoryPort {
    CompletableFuture<InventoryReservationResult> reserveInventory(
        SkuCode skuCode, int quantity);
}
```

### PaymentPort

```java
public interface PaymentPort {
    CompletableFuture<PaymentResult> processPayment(
        OrderId orderId, Money amount, String idempotencyKey);
}
```

### ShippingPort

```java
public interface ShippingPort {
    CompletableFuture<ShippingResult> createShipment(
        OrderId orderId, String address, List<OrderItem> items);
}
```

---

## Mapper Responsibilities

| Mapper | Location | Converts |
|--------|----------|----------|
| OrderWebMapper | infrastructure/adapter/in/web/mapper | CreateOrderRequest ↔ CreateOrderCommand |
| InventoryMapper | infrastructure/adapter/out/inventory/mapper | Domain ↔ InventoryRequest/Response |
| PaymentMapper | infrastructure/adapter/out/payment/mapper | Domain ↔ PaymentRequest/Response |
| ShippingMapper | infrastructure/adapter/out/shipping/mapper | Domain ↔ ShippingRequest/Response |

All mappers reside in Infrastructure layer per constitution requirements.
