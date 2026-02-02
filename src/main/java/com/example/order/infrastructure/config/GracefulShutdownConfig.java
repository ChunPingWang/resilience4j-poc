package com.example.order.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles graceful shutdown by waiting for in-flight requests to complete.
 * Part of Strategy 1: Graceful Shutdown.
 */
@Component
public class GracefulShutdownConfig implements ApplicationListener<ContextClosedEvent> {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdownConfig.class);
    private static final int MAX_WAIT_SECONDS = 25;

    private final AtomicInteger activeRequests = new AtomicInteger(0);

    /**
     * Increments the active request counter.
     * Called when a new request starts processing.
     */
    public void incrementActiveRequests() {
        int count = activeRequests.incrementAndGet();
        log.debug("Request started. Active requests: {}", count);
    }

    /**
     * Decrements the active request counter.
     * Called when a request finishes processing.
     */
    public void decrementActiveRequests() {
        int count = activeRequests.decrementAndGet();
        log.debug("Request completed. Active requests: {}", count);
    }

    /**
     * Returns the current count of active requests.
     */
    public int getActiveRequestCount() {
        return activeRequests.get();
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        log.info("Shutdown signal received. Active requests: {}", activeRequests.get());

        int waitSeconds = MAX_WAIT_SECONDS;
        while (activeRequests.get() > 0 && waitSeconds > 0) {
            log.info("Waiting for {} active request(s) to complete... ({} seconds remaining)",
                    activeRequests.get(), waitSeconds);
            try {
                Thread.sleep(1000);
                waitSeconds--;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Shutdown interrupted while waiting for requests to complete");
                break;
            }
        }

        if (activeRequests.get() > 0) {
            log.warn("Graceful shutdown timeout. {} request(s) may be interrupted.",
                    activeRequests.get());
        } else {
            log.info("Graceful shutdown complete. All requests finished.");
        }
    }
}
