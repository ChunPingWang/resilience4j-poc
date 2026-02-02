package com.example.order.infrastructure.outbox;

import com.example.order.infrastructure.persistence.entity.OutboxEvent;
import com.example.order.infrastructure.persistence.entity.OutboxEventStatus;
import com.example.order.infrastructure.persistence.repository.OutboxRepository;
import com.example.order.infrastructure.saga.SagaOrchestrator;
import com.example.order.infrastructure.saga.SagaResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Outbox poller that processes pending events.
 * Part of Strategy 3: Outbox Pattern.
 */
@Component
@ConditionalOnProperty(value = "outbox.poller.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxRepository outboxRepository;
    private final SagaOrchestrator sagaOrchestrator;
    private final int batchSize;
    private final int maxRetries;

    public OutboxPoller(
            OutboxRepository outboxRepository,
            SagaOrchestrator sagaOrchestrator,
            @Value("${outbox.poller.batch-size:100}") int batchSize,
            @Value("${outbox.poller.max-retries:3}") int maxRetries) {
        this.outboxRepository = outboxRepository;
        this.sagaOrchestrator = sagaOrchestrator;
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
    }

    /**
     * Polls and processes pending outbox events.
     * Runs at a fixed interval configured in application.yml.
     */
    @Scheduled(fixedDelayString = "${outbox.poller.interval-ms:1000}")
    public void pollAndProcess() {
        List<OutboxEvent> events = outboxRepository.findPendingEvents(batchSize);

        if (!events.isEmpty()) {
            log.debug("Processing {} pending outbox events", events.size());
        }

        for (OutboxEvent event : events) {
            processEvent(event);
        }
    }

    /**
     * Retries failed events that haven't exceeded max retries.
     * Runs every 30 seconds.
     */
    @Scheduled(fixedRate = 30000)
    public void retryFailedEvents() {
        List<OutboxEvent> failedEvents = outboxRepository.findFailedEventsForRetry(maxRetries, batchSize);

        if (!failedEvents.isEmpty()) {
            log.info("Retrying {} failed outbox events", failedEvents.size());
        }

        for (OutboxEvent event : failedEvents) {
            event.markRetrying();
            outboxRepository.save(event);
            processEvent(event);
        }
    }

    /**
     * Cleans up old processed events.
     * Runs every hour.
     */
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupProcessedEvents() {
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        int deleted = outboxRepository.deleteProcessedEventsBefore(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} processed outbox events older than 24 hours", deleted);
        }
    }

    private void processEvent(OutboxEvent event) {
        log.debug("Processing outbox event: {} (type: {}, aggregate: {})",
                event.getId(), event.getEventType(), event.getAggregateId());

        try {
            // Mark as processing to prevent concurrent processing
            event.setStatus(OutboxEventStatus.PROCESSING);
            outboxRepository.save(event);

            if ("OrderCreated".equals(event.getEventType())) {
                processOrderCreatedEvent(event);
            } else {
                log.warn("Unknown event type: {}", event.getEventType());
                event.markFailed("Unknown event type: " + event.getEventType());
                outboxRepository.save(event);
            }
        } catch (Exception e) {
            log.error("Failed to process outbox event: {}", event.getId(), e);
            event.markFailed(e.getMessage());
            outboxRepository.save(event);
        }
    }

    private void processOrderCreatedEvent(OutboxEvent event) {
        String orderId = event.getAggregateId();

        CompletableFuture<SagaResult> sagaFuture = sagaOrchestrator.executeSaga(orderId);

        // Process synchronously for now (could be made async with proper handling)
        try {
            SagaResult result = sagaFuture.join();

            if (result.success()) {
                event.markProcessed();
                log.info("Successfully processed OrderCreated event for order: {}", orderId);
            } else {
                event.markFailed(result.errorMessage());
                log.warn("Saga failed for order: {} - {}", orderId, result.errorMessage());
            }
        } catch (Exception e) {
            event.markFailed(e.getMessage());
            log.error("Exception during saga execution for order: {}", orderId, e);
        }

        outboxRepository.save(event);
    }
}
