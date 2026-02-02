package com.example.order.integration;

import com.example.order.support.WireMockTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for idempotency handling.
 * Part of Strategy 2: Client Retry + Idempotency.
 */
@DisplayName("Idempotency Integration Tests")
class IdempotencyIntegrationTest extends WireMockTestSupport {

    @Autowired
    private WebTestClient webTestClient;

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
    @DisplayName("should return same result for duplicate request with same idempotency key")
    void should_return_same_result_for_duplicate_request() {
        // Given
        String idempotencyKey = UUID.randomUUID().toString();
        stubInventorySuccess("SKU001", 98);
        stubPaymentSuccess("TXN-123");
        stubShippingSuccess("TRK-456");

        // When - First request
        String firstOrderId = webTestClient.post()
                .uri("/api/orders")
                .header("X-Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ORDER_REQUEST)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.orderId").isNotEmpty()
                .jsonPath("$.status").isEqualTo("COMPLETED")
                .returnResult()
                .getResponseBody() != null ? "extracted" : null;

        // When - Second request with same idempotency key
        webTestClient.post()
                .uri("/api/orders")
                .header("X-Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ORDER_REQUEST)
                .exchange()
                .expectStatus().isOk()  // Should be 200 OK (cached response)
                .expectBody()
                .jsonPath("$.status").isEqualTo("COMPLETED");

        // Then - Service should only be called once (first request)
        verifyInventoryCalledTimes(1);
        verifyPaymentCalledTimes(1);
        verifyShippingCalledTimes(1);
    }

    @Test
    @DisplayName("should process different requests with different idempotency keys")
    void should_process_different_requests_separately() {
        // Given
        String idempotencyKey1 = UUID.randomUUID().toString();
        String idempotencyKey2 = UUID.randomUUID().toString();
        stubInventorySuccess("SKU001", 98);
        stubPaymentSuccess("TXN-123");
        stubShippingSuccess("TRK-456");

        // When - First request
        webTestClient.post()
                .uri("/api/orders")
                .header("X-Idempotency-Key", idempotencyKey1)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ORDER_REQUEST)
                .exchange()
                .expectStatus().isCreated();

        // When - Second request with different idempotency key
        webTestClient.post()
                .uri("/api/orders")
                .header("X-Idempotency-Key", idempotencyKey2)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ORDER_REQUEST)
                .exchange()
                .expectStatus().isCreated();

        // Then - Service should be called twice (both requests processed)
        verifyInventoryCalledTimes(2);
        verifyPaymentCalledTimes(2);
        verifyShippingCalledTimes(2);
    }

    @Test
    @DisplayName("should generate idempotency key if not provided")
    void should_generate_idempotency_key_if_not_provided() {
        // Given
        stubInventorySuccess("SKU001", 98);
        stubPaymentSuccess("TXN-123");
        stubShippingSuccess("TRK-456");

        // When - Request without idempotency key
        webTestClient.post()
                .uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ORDER_REQUEST)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.orderId").isNotEmpty()
                .jsonPath("$.status").isEqualTo("COMPLETED");

        // Then
        verifyInventoryCalledTimes(1);
    }

    @Test
    @DisplayName("should return cached failure result for duplicate failed request")
    void should_return_cached_failure_result() {
        // Given
        String idempotencyKey = UUID.randomUUID().toString();
        stubInventoryPermanentFailure();

        // When - First request (will fail)
        webTestClient.post()
                .uri("/api/orders")
                .header("X-Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ORDER_REQUEST)
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.status").isEqualTo("FAILED");

        // Reset WireMock and set up success (to verify cache is used)
        inventoryServer.resetAll();
        stubInventorySuccess("SKU001", 98);
        stubPaymentSuccess("TXN-123");
        stubShippingSuccess("TRK-456");

        // When - Second request with same idempotency key
        webTestClient.post()
                .uri("/api/orders")
                .header("X-Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ORDER_REQUEST)
                .exchange()
                .expectStatus().isOk()  // Returns cached result
                .expectBody()
                .jsonPath("$.status").isEqualTo("FAILED");  // Still failed (cached)

        // Then - New services should not be called (cached failure returned)
        verifyInventoryCalledTimes(0);
    }
}
