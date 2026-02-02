package com.example.order.infrastructure.exception;

/**
 * Exception thrown when a service is unavailable after all recovery attempts.
 * This is the final fallback exception when retries are exhausted or circuit breaker is open.
 */
public class ServiceUnavailableException extends RuntimeException {

    private final String serviceName;

    public ServiceUnavailableException(String serviceName, String message) {
        super(message);
        this.serviceName = serviceName;
    }

    public ServiceUnavailableException(String serviceName, String message, Throwable cause) {
        super(message, cause);
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }
}
