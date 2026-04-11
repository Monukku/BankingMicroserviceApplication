package com.rewabank.audit.repository;

import com.rewabank.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    boolean existsByEventId(String eventId);

    Page<AuditLog> findByKeycloakUserIdOrderByRecordedAtDesc(
            String keycloakUserId, Pageable pageable);

    Page<AuditLog> findByAggregateIdOrderByRecordedAtDesc(
            String aggregateId, Pageable pageable);

    Page<AuditLog> findByEventTypeOrderByRecordedAtDesc(
            String eventType, Pageable pageable);

    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.recordedAt BETWEEN :from AND :to
        ORDER BY a.recordedAt DESC
        """)
    Page<AuditLog> findByDateRange(
            LocalDateTime from, LocalDateTime to, Pageable pageable);
}
