package com.rewabank.audit.repository;

import com.rewabank.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AuditLogRepository extends MongoRepository<AuditLog, String> {

    boolean existsByEventId(String eventId);

    Page<AuditLog> findByKeycloakUserIdOrderByRecordedAtDesc(
            String keycloakUserId, Pageable pageable);

    Page<AuditLog> findByAggregateIdOrderByRecordedAtDesc(
            String aggregateId, Pageable pageable);

    Page<AuditLog> findByEventTypeOrderByRecordedAtDesc(
            String eventType, Pageable pageable);

    @Query("{ 'recordedAt': { $gte: ?0, $lte: ?1 } }")
    Page<AuditLog> findByDateRange(
            LocalDateTime from, LocalDateTime to, Pageable pageable);
}