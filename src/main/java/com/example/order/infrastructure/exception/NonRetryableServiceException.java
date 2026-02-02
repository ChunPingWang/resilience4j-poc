package com.example.order.infrastructure.exception;

/**
 * Exception for service errors that should NOT trigger a retry.
 * Typically thrown for 4xx HTTP errors (except business errors).
 */
public class NonRetryableServiceException extends RuntimeException {

    private final String serviceName;
    private final int statusCode;

    public NonRetryableServiceException(String serviceName, int statusCode, String message) {
        super(message);
        this.serviceName = serviceName;
        this.statusCode = statusCode;
    }

    public NonRetryableServiceException(String serviceName, int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.serviceName = serviceName;
        this.statusCode = statusCode;
    }

    public String getServiceName() {
        return serviceName;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
