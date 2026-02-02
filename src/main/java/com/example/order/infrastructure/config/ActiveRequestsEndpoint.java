package com.example.order.infrastructure.config;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Actuator endpoint to expose active request count.
 * Useful for monitoring during graceful shutdown.
 */
@Component
@Endpoint(id = "activerequests")
public class ActiveRequestsEndpoint {

    private final GracefulShutdownConfig gracefulShutdownConfig;

    public ActiveRequestsEndpoint(GracefulShutdownConfig gracefulShutdownConfig) {
        this.gracefulShutdownConfig = gracefulShutdownConfig;
    }

    @ReadOperation
    public Map<String, Object> activeRequests() {
        return Map.of(
                "activeRequests", gracefulShutdownConfig.getActiveRequestCount(),
                "status", gracefulShutdownConfig.getActiveRequestCount() > 0 ? "BUSY" : "IDLE"
        );
    }
}
