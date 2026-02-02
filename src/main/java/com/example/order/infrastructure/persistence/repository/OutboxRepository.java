package com.example.order.infrastructure.persistence.repository;

import com.example.order.infrastructure.persistence.entity.OutboxEvent;
import com.example.order.infrastructure.persistence.entity.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * JPA Repository for OutboxEvent entities.
 */
@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, String> {

    @Query("SELECT o FROM OutboxEvent o WHERE o.status = :status ORDER BY o.createdAt ASC")
    List<OutboxEvent> findByStatus(@Param("status") OutboxEventStatus status);

    @Query("SELECT o FROM OutboxEvent o WHERE o.status = 'PENDING' ORDER BY o.createdAt ASC LIMIT :limit")
    List<OutboxEvent> findPendingEvents(@Param("limit") int limit);

    @Query("SELECT o FROM OutboxEvent o WHERE o.status = 'FAILED' AND o.retryCount < :maxRetries ORDER BY o.createdAt ASC LIMIT :limit")
    List<OutboxEvent> findFailedEventsForRetry(@Param("maxRetries") int maxRetries, @Param("limit") int limit);

    @Modifying
    @Query("DELETE FROM OutboxEvent o WHERE o.status = 'PROCESSED' AND o.processedAt < :before")
    int deleteProcessedEventsBefore(@Param("before") Instant before);

    List<OutboxEvent> findByAggregateId(String aggregateId);
}
