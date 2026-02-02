package com.example.order.infrastructure.service;

import com.example.order.application.dto.OrderResult;
import com.example.order.infrastructure.persistence.entity.IdempotencyRecord;
import com.example.order.infrastructure.persistence.entity.IdempotencyStatus;
import com.example.order.infrastructure.persistence.repository.IdempotencyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Service for handling request idempotency.
 * Part of Strategy 2: Client Retry + Idempotency.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRepository repository;
    private final ObjectMapper objectMapper;
    private final int expiryHours;

    public IdempotencyService(
            IdempotencyRepository repository,
            ObjectMapper objectMapper,
            @Value("${idempotency.expiry-hours:24}") int expiryHours) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.expiryHours = expiryHours;
    }

    /**
     * Checks if a request with the given idempotency key has already been processed.
     *
     * @param idempotencyKey the idempotency key
     * @return Optional containing the previous result if found and not expired
     */
    @Transactional(readOnly = true)
    public Optional<OrderResult> getExistingResult(String idempotencyKey) {
        return repository.findValidByIdempotencyKey(idempotencyKey, Instant.now())
                .filter(record -> record.getStatus() == IdempotencyStatus.COMPLETED)
                .map(record -> {
                    try {
                        return objectMapper.readValue(record.getResponse(), OrderResult.class);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize idempotency response for key: {}", idempotencyKey, e);
                        return null;
                    }
                });
    }

    /**
     * Checks if a request with the given idempotency key is currently in progress.
     *
     * @param idempotencyKey the idempotency key
     * @return true if request is in progress
     */
    @Transactional(readOnly = true)
    public boolean isInProgress(String idempotencyKey) {
        return repository.findValidByIdempotencyKey(idempotencyKey, Instant.now())
                .map(record -> record.getStatus() == IdempotencyStatus.IN_PROGRESS)
                .orElse(false);
    }

    /**
     * Marks a request as in progress. Used to prevent concurrent duplicate requests.
     *
     * @param idempotencyKey the idempotency key
     * @param orderId        the order ID being processed
     * @return true if marked successfully, false if already exists
     */
    @Transactional
    public boolean markInProgress(String idempotencyKey, String orderId) {
        if (repository.existsByIdempotencyKey(idempotencyKey)) {
            log.debug("Idempotency key already exists: {}", idempotencyKey);
            return false;
        }

        IdempotencyRecord record = new IdempotencyRecord();
        record.setIdempotencyKey(idempotencyKey);
        record.setOrderId(orderId);
        record.setStatus(IdempotencyStatus.IN_PROGRESS);
        record.setExpiresAt(Instant.now().plus(expiryHours, ChronoUnit.HOURS));

        repository.save(record);
        log.debug("Marked idempotency key as in progress: {}", idempotencyKey);
        return true;
    }

    /**
     * Saves the result of a completed request.
     *
     * @param idempotencyKey the idempotency key
     * @param result         the order result
     */
    @Transactional
    public void saveResult(String idempotencyKey, OrderResult result) {
        repository.findByIdempotencyKey(idempotencyKey)
                .ifPresentOrElse(
                        record -> {
                            try {
                                record.setResponse(objectMapper.writeValueAsString(result));
                                record.setStatus(IdempotencyStatus.COMPLETED);
                                repository.save(record);
                                log.debug("Saved result for idempotency key: {}", idempotencyKey);
                            } catch (JsonProcessingException e) {
                                log.error("Failed to serialize result for idempotency key: {}", idempotencyKey, e);
                            }
                        },
                        () -> log.warn("No idempotency record found for key: {}", idempotencyKey)
                );
    }

    /**
     * Marks a request as failed.
     *
     * @param idempotencyKey the idempotency key
     */
    @Transactional
    public void markFailed(String idempotencyKey) {
        repository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(record -> {
                    record.setStatus(IdempotencyStatus.FAILED);
                    repository.save(record);
                    log.debug("Marked idempotency key as failed: {}", idempotencyKey);
                });
    }

    /**
     * Cleans up expired idempotency records.
     * Runs every hour.
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    @Transactional
    public void cleanupExpiredRecords() {
        int deleted = repository.deleteExpiredRecords(Instant.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired idempotency records", deleted);
        }
    }
}
