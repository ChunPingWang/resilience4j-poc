package com.example.order.unit.domain;

import com.example.order.domain.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Order aggregate and related domain objects.
 */
@DisplayName("Order Domain Tests")
class OrderTest {

    @Nested
    @DisplayName("Order Creation")
    class OrderCreation {

        @Test
        @DisplayName("should_create_order_with_valid_items")
        void should_create_order_with_valid_items() {
            // Given
            List<OrderItem> items = List.of(
                    OrderItem.of(SkuCode.of("SKU001"), 2, Money.of(new BigDecimal("1500.00"))),
                    OrderItem.of(SkuCode.of("SKU002"), 1, Money.of(new BigDecimal("2000.00")))
            );

            // When
            Order order = Order.create(items, "台北市信義區松仁路100號");

            // Then
            assertThat(order.getOrderId()).isNotNull();
            assertThat(order.getItems()).hasSize(2);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.getShippingAddress()).isEqualTo("台北市信義區松仁路100號");
            assertThat(order.getPaymentIdempotencyKey()).isNotNull();
            assertThat(order.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should_calculate_total_amount_correctly")
        void should_calculate_total_amount_correctly() {
            // Given: 2 items - SKU001 x 2 @ 1500, SKU002 x 3 @ 500
            List<OrderItem> items = List.of(
                    OrderItem.of(SkuCode.of("SKU001"), 2, Money.of(new BigDecimal("1500.00"))),
                    OrderItem.of(SkuCode.of("SKU002"), 3, Money.of(new BigDecimal("500.00")))
            );

            // When
            Order order = Order.create(items, "台北市");

            // Then: (2 * 1500) + (3 * 500) = 3000 + 1500 = 4500
            Money expectedTotal = Money.of(new BigDecimal("4500.00"));
            assertThat(order.getTotalAmount()).isEqualTo(expectedTotal);
        }

        @Test
        @DisplayName("should_reject_empty_items_list")
        void should_reject_empty_items_list() {
            // When & Then
            assertThatThrownBy(() -> Order.create(Collections.emptyList(), "台北市"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least one item");
        }

        @Test
        @DisplayName("should_reject_null_shipping_address")
        void should_reject_null_shipping_address() {
            // Given
            List<OrderItem> items = List.of(
                    OrderItem.of(SkuCode.of("SKU001"), 1, Money.of(new BigDecimal("100.00")))
            );

            // When & Then
            assertThatThrownBy(() -> Order.create(items, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Order State Transitions")
    class OrderStateTransitions {

        @Test
        @DisplayName("should_transition_through_valid_states")
        void should_transition_through_valid_states() {
            // Given
            Order order = createValidOrder();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);

            // When & Then: PENDING -> INVENTORY_RESERVED
            order.markInventoryReserved();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.INVENTORY_RESERVED);

            // When & Then: INVENTORY_RESERVED -> PAYMENT_COMPLETED
            order.markPaymentCompleted();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_COMPLETED);

            // When & Then: PAYMENT_COMPLETED -> SHIPPING_REQUESTED
            order.markShippingRequested();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPING_REQUESTED);

            // When & Then: SHIPPING_REQUESTED -> COMPLETED
            order.markCompleted();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        }

        @Test
        @DisplayName("should_reject_invalid_state_transition")
        void should_reject_invalid_state_transition() {
            // Given: Order in PENDING state
            Order order = createValidOrder();

            // When & Then: Cannot skip to PAYMENT_COMPLETED
            assertThatThrownBy(order::markPaymentCompleted)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from PENDING to PAYMENT_COMPLETED");
        }

        @Test
        @DisplayName("should_allow_marking_failed_from_any_non_completed_state")
        void should_allow_marking_failed_from_any_non_completed_state() {
            // Given
            Order order = createValidOrder();
            order.markInventoryReserved();

            // When
            order.markFailed();

            // Then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        }

        @Test
        @DisplayName("should_reject_marking_completed_order_as_failed")
        void should_reject_marking_completed_order_as_failed() {
            // Given: Completed order
            Order order = createValidOrder();
            order.markInventoryReserved();
            order.markPaymentCompleted();
            order.markShippingRequested();
            order.markCompleted();

            // When & Then
            assertThatThrownBy(order::markFailed)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot fail a completed order");
        }
    }

    @Nested
    @DisplayName("Value Objects")
    class ValueObjectsTests {

        @Test
        @DisplayName("OrderId should validate UUID format")
        void orderId_should_validate_uuid_format() {
            // Valid UUID
            assertThatCode(() -> OrderId.of("550e8400-e29b-41d4-a716-446655440000"))
                    .doesNotThrowAnyException();

            // Invalid UUID
            assertThatThrownBy(() -> OrderId.of("invalid-uuid"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid OrderId format");
        }

        @Test
        @DisplayName("SkuCode should validate pattern")
        void skuCode_should_validate_pattern() {
            // Valid patterns
            assertThatCode(() -> SkuCode.of("SKU001")).doesNotThrowAnyException();
            assertThatCode(() -> SkuCode.of("PRD123")).doesNotThrowAnyException();
            assertThatCode(() -> SkuCode.of("ABC999")).doesNotThrowAnyException();

            // Invalid patterns
            assertThatThrownBy(() -> SkuCode.of("sku001"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid SkuCode format");

            assertThatThrownBy(() -> SkuCode.of("SK001"))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> SkuCode.of("SKUABC"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Money should support arithmetic operations")
        void money_should_support_arithmetic_operations() {
            Money m1 = Money.of(new BigDecimal("100.00"));
            Money m2 = Money.of(new BigDecimal("50.00"));

            // Add
            Money sum = m1.add(m2);
            assertThat(sum.getAmount()).isEqualByComparingTo(new BigDecimal("150.00"));

            // Multiply
            Money product = m1.multiply(3);
            assertThat(product.getAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
        }

        @Test
        @DisplayName("Money should reject negative amounts")
        void money_should_reject_negative_amounts() {
            assertThatThrownBy(() -> Money.of(new BigDecimal("-100.00")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be negative");
        }

        @Test
        @DisplayName("Money should reject adding different currencies")
        void money_should_reject_adding_different_currencies() {
            Money twd = Money.of(new BigDecimal("100.00"), "TWD");
            Money usd = Money.of(new BigDecimal("50.00"), "USD");

            assertThatThrownBy(() -> twd.add(usd))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("different currencies");
        }
    }

    @Nested
    @DisplayName("OrderItem")
    class OrderItemTests {

        @Test
        @DisplayName("should_calculate_subtotal_correctly")
        void should_calculate_subtotal_correctly() {
            // Given
            OrderItem item = OrderItem.of(
                    SkuCode.of("SKU001"),
                    5,
                    Money.of(new BigDecimal("200.00"))
            );

            // When
            Money subtotal = item.getSubtotal();

            // Then
            assertThat(subtotal.getAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
        }

        @Test
        @DisplayName("should_reject_non_positive_quantity")
        void should_reject_non_positive_quantity() {
            assertThatThrownBy(() -> OrderItem.of(
                    SkuCode.of("SKU001"),
                    0,
                    Money.of(new BigDecimal("100.00"))
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Quantity must be positive");

            assertThatThrownBy(() -> OrderItem.of(
                    SkuCode.of("SKU001"),
                    -1,
                    Money.of(new BigDecimal("100.00"))
            ))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private Order createValidOrder() {
        return Order.create(
                List.of(OrderItem.of(SkuCode.of("SKU001"), 1, Money.of(new BigDecimal("100.00")))),
                "台北市信義區"
        );
    }
}
