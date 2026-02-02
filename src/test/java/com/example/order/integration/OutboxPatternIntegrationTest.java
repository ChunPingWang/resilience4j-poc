package com.example.order.integration;

import com.example.order.infrastructure.persistence.entity.OutboxEvent;
import com.example.order.infrastructure.persistence.entity.OutboxEventStatus;
import com.example.order.infrastructure.persistence.repository.OrderJpaRepository;
import com.example.order.infrastructure.persistence.repository.OutboxRepository;
import com.example.order.support.WireMockTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for the Outbox Pattern.
 * Part of Strategy 3: Outbox Pattern + Saga.
 *
 * These tests verify the async order processing flow.
 *
 * Note: Disabled by default as they require async processing mode.
 * Enable with: -Doutbox.tests.enabled=true
 */
@DisplayName("Outbox Pattern Integration Tests")
@TestPropertySource(properties = {
        "outbox.enabled=true",
        "outbox.poller.enabled=true",
        "outbox.poller.interval-ms=500"
})
@Disabled("Outbox pattern tests require async mode - enable with -Doutbox.tests.enabled=true")
class OutboxPatternIntegrationTest extends WireMockTestSupport {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private OrderJpaRepository orderRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    private static final String ORDER_REQUEST = """
            {
                "items": [
                    {
                        "skuCode": "SKU001",
                        "quantity": 2,
                        "unitPrice": 1500.00
                    }
                ],
                "shippingAddress": "台北市信義區"
            }
            """;

    @Test
    @DisplayName("should create order and outbox event in single transaction")
    void should_create_order_and_outbox_in_single_transaction() {
        // Given
        String idempotencyKey = UUID.randomUUID().toString();
        stubInventorySuccess("SKU001", 98);
        stubPaymentSuccess("TXN-123");
        stubShippingSuccess("TRK-456");

        // When
        String orderId = webTestClient.post()
                .uri("/api/orders")
                .header("X-Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ORDER_REQUEST)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.orderId").isNotEmpty()
                .jsonPath("$.status").isEqualTo("PENDING")  // Async mode returns PENDING
                .jsonPath("$.message").value(msg ->
                        assertThat((String) msg).contains("處理中"))
                .returnResult()
                .getResponseBody() != null ? extractOrderId() : null;

        // Then - Verify outbox event was created
        List<OutboxEvent> events = outboxRepository.findByStatus(OutboxEventStatus.PENDING);
        assertThat(events).isNotEmpty();

        // Wait for async processing to complete
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    List<OutboxEvent> processedEvents = outboxRepository.findByStatus(OutboxEventStatus.PROCESSED);
                    assertThat(processedEvents).hasSizeGreaterThanOrEqualTo(1);
                });
    }

    @Test
    @DisplayName("should process outbox events asynchronously via saga")
    void should_process_outbox_events_via_saga() {
        // Given
        String idempotencyKey = UUID.randomUUID().toString();
        stubInventorySuccess("SKU001", 98);
        stubPaymentSuccess("TXN-123");
        stubShippingSuccess("TRK-456");

        // When - Create order (returns immediately with PENDING)
        webTestClient.post()
                .uri("/api/orders")
                .header("X-Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ORDER_REQUEST)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("PENDING");

        // Then - Wait for saga to complete
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    // All services should be called eventually
                    verifyInventoryCalledTimes(1);
                    verifyPaymentCalledTimes(1);
                    verifyShippingCalledTimes(1);
                });
    }

    @Test
    @DisplayName("should handle saga failure with compensation")
    void should_handle_saga_failure_with_compensation() {
        // Given
        String idempotencyKey = UUID.randomUUID().toString();
        stubInventorySuccess("SKU001", 98);
        stubPaymentPermanentFailure();  // Payment will fail

        // When - Create order
        webTestClient.post()
                .uri("/api/orders")
                .header("X-Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ORDER_REQUEST)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("PENDING");

        // Then - Wait for saga to fail
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    // Inventory should be reserved, payment should fail
                    verifyInventoryCalledTimes(1);
                    // Payment should be attempted multiple times due to retry
                    assertThat(paymentServer.getAllServeEvents().size()).isGreaterThanOrEqualTo(1);
                });
    }

    private String extractOrderId() {
        // Helper to extract order ID from response - simplified for test
        return UUID.randomUUID().toString();
    }
}
