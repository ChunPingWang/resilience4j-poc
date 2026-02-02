package com.example.order.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Base class for integration tests that use WireMock.
 * Provides WireMock servers for inventory, payment, and shipping services.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class WireMockTestSupport {

    protected static WireMockServer inventoryServer;
    protected static WireMockServer paymentServer;
    protected static WireMockServer shippingServer;

    @BeforeAll
    static void startWireMockServers() {
        inventoryServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        paymentServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        shippingServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());

        inventoryServer.start();
        paymentServer.start();
        shippingServer.start();
    }

    @AfterAll
    static void stopWireMockServers() {
        inventoryServer.stop();
        paymentServer.stop();
        shippingServer.stop();
    }

    @AfterEach
    void resetWireMockServers() {
        inventoryServer.resetAll();
        paymentServer.resetAll();
        shippingServer.resetAll();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("services.inventory.base-url", () -> inventoryServer.baseUrl());
        registry.add("services.payment.base-url", () -> paymentServer.baseUrl());
        registry.add("services.shipping.base-url", () -> shippingServer.baseUrl());
    }

    // ==================== Inventory Stubs ====================

    /**
     * Stubs inventory service to return success response.
     */
    protected void stubInventorySuccess(String skuCode, int remainingQty) {
        inventoryServer.stubFor(post(urlEqualTo("/api/inventory/deduct"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "skuCode": "%s",
                                    "reserved": true,
                                    "remainingQty": %d
                                }
                                """.formatted(skuCode, remainingQty))));
    }

    /**
     * Stubs inventory service to return insufficient stock (409).
     */
    protected void stubInventoryInsufficientStock(String skuCode, int remainingQty) {
        inventoryServer.stubFor(post(urlEqualTo("/api/inventory/deduct"))
                .willReturn(aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": "INSUFFICIENT_STOCK",
                                    "message": "Insufficient stock for SKU %s"
                                }
                                """.formatted(skuCode))));
    }

    /**
     * Stubs inventory service to fail transiently then succeed.
     * Uses WireMock Scenarios for stateful behavior.
     */
    protected void stubInventoryTransientFailureThenSuccess(String skuCode, int failCount, int remainingQty) {
        String scenarioName = "InventoryTransientFailure";

        for (int i = 0; i < failCount; i++) {
            String currentState = i == 0 ? Scenario.STARTED : "Attempt" + i;
            String nextState = "Attempt" + (i + 1);

            inventoryServer.stubFor(post(urlEqualTo("/api/inventory/deduct"))
                    .inScenario(scenarioName)
                    .whenScenarioStateIs(currentState)
                    .willSetStateTo(nextState)
                    .willReturn(aResponse()
                            .withStatus(503)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                        "code": "SERVICE_UNAVAILABLE",
                                        "message": "Inventory service temporarily unavailable"
                                    }
                                    """)));
        }

        inventoryServer.stubFor(post(urlEqualTo("/api/inventory/deduct"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("Attempt" + failCount)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "skuCode": "%s",
                                    "reserved": true,
                                    "remainingQty": %d
                                }
                                """.formatted(skuCode, remainingQty))));
    }

    /**
     * Stubs inventory service to always fail with 503.
     */
    protected void stubInventoryPermanentFailure() {
        inventoryServer.stubFor(post(urlEqualTo("/api/inventory/deduct"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": "SERVICE_UNAVAILABLE",
                                    "message": "Inventory service unavailable"
                                }
                                """)));
    }

    /**
     * Stubs inventory service to return 400 Bad Request.
     */
    protected void stubInventoryBadRequest() {
        inventoryServer.stubFor(post(urlEqualTo("/api/inventory/deduct"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": "BAD_REQUEST",
                                    "message": "Invalid request parameters"
                                }
                                """)));
    }

    // ==================== Payment Stubs ====================

    /**
     * Stubs payment service to return success response.
     */
    protected void stubPaymentSuccess(String transactionId) {
        paymentServer.stubFor(post(urlEqualTo("/api/payments/charge"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "transactionId": "%s",
                                    "status": "SUCCESS",
                                    "message": "Payment processed successfully"
                                }
                                """.formatted(transactionId))));
    }

    /**
     * Stubs payment service to always fail with 500.
     */
    protected void stubPaymentPermanentFailure() {
        paymentServer.stubFor(post(urlEqualTo("/api/payments/charge"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": "INTERNAL_ERROR",
                                    "message": "Payment gateway internal error"
                                }
                                """)));
    }

    /**
     * Stubs payment service to return insufficient balance (409).
     */
    protected void stubPaymentInsufficientBalance() {
        paymentServer.stubFor(post(urlEqualTo("/api/payments/charge"))
                .willReturn(aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": "INSUFFICIENT_BALANCE",
                                    "message": "Insufficient balance for payment"
                                }
                                """)));
    }

    // ==================== Shipping Stubs ====================

    /**
     * Stubs shipping service to return success response.
     */
    protected void stubShippingSuccess(String trackingNumber) {
        shippingServer.stubFor(post(urlEqualTo("/api/shipping/create"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "trackingNumber": "%s",
                                    "status": "CREATED",
                                    "message": "Shipment created successfully"
                                }
                                """.formatted(trackingNumber))));
    }

    /**
     * Stubs shipping service with a delay (for timeout testing).
     */
    protected void stubShippingWithDelay(String trackingNumber, int delayMs) {
        shippingServer.stubFor(post(urlEqualTo("/api/shipping/create"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(delayMs)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "trackingNumber": "%s",
                                    "status": "CREATED",
                                    "message": "Shipment created successfully"
                                }
                                """.formatted(trackingNumber))));
    }

    /**
     * Stubs shipping service to always fail with 503.
     */
    protected void stubShippingPermanentFailure() {
        shippingServer.stubFor(post(urlEqualTo("/api/shipping/create"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": "SERVICE_UNAVAILABLE",
                                    "message": "Shipping service unavailable"
                                }
                                """)));
    }

    // ==================== Verification Helpers ====================

    /**
     * Verifies that inventory service was called exactly n times.
     */
    protected void verifyInventoryCalledTimes(int count) {
        inventoryServer.verify(count, postRequestedFor(urlEqualTo("/api/inventory/deduct")));
    }

    /**
     * Verifies that payment service was called exactly n times.
     */
    protected void verifyPaymentCalledTimes(int count) {
        paymentServer.verify(count, postRequestedFor(urlEqualTo("/api/payments/charge")));
    }

    /**
     * Verifies that shipping service was called exactly n times.
     */
    protected void verifyShippingCalledTimes(int count) {
        shippingServer.verify(count, postRequestedFor(urlEqualTo("/api/shipping/create")));
    }

    /**
     * Verifies that payment service was called with a specific idempotency key.
     */
    protected void verifyPaymentCalledWithIdempotencyKey(String idempotencyKey) {
        paymentServer.verify(postRequestedFor(urlEqualTo("/api/payments/charge"))
                .withHeader("Idempotency-Key", equalTo(idempotencyKey)));
    }
}
