package com.example.order.infrastructure.exception;

/**
 * Exception for service errors that should trigger a retry.
 * Typically thrown for 5xx HTTP errors and network issues.
 */
public class RetryableServiceException extends RuntimeException {

    private final String serviceName;
    private final int statusCode;

    public RetryableServiceException(String serviceName, int statusCode, String message) {
        super(message);
        this.serviceName = serviceName;
        this.statusCode = statusCode;
    }

    public RetryableServiceException(String serviceName, int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.serviceName = serviceName;
        this.statusCode = statusCode;
    }

    public RetryableServiceException(String serviceName, String message, Throwable cause) {
        super(message, cause);
        this.serviceName = serviceName;
        this.statusCode = 0;
    }

    public String getServiceName() {
        return serviceName;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
