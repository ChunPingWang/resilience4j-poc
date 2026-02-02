package com.example.order.infrastructure.persistence.repository;

import com.example.order.infrastructure.persistence.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * JPA Repository for IdempotencyRecord entities.
 */
@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, String> {

    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    @Query("SELECT i FROM IdempotencyRecord i WHERE i.idempotencyKey = :key AND i.expiresAt > :now")
    Optional<IdempotencyRecord> findValidByIdempotencyKey(@Param("key") String idempotencyKey, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM IdempotencyRecord i WHERE i.expiresAt < :before")
    int deleteExpiredRecords(@Param("before") Instant before);
}
