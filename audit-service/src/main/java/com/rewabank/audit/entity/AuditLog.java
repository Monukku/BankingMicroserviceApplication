package com.rewabank.audit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Append-only audit log.
 * @Immutable ensures Hibernate never attempts UPDATE.
 * No soft delete — records are permanent.
 * Partitioned by month in PostgreSQL for query performance.
 * 7-year retention enforced by database policy.
 */
@Entity
@Table(name = "audit_log")
@Immutable                      // Hibernate never issues UPDATE SQL
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // From Kafka event
    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    // Who did it
    @Column(name = "keycloak_user_id")
    private String keycloakUserId;

    // What it affected
    @Column(name = "aggregate_id")
    private String aggregateId;

    @Column(name = "aggregate_type", length = 60)
    private String aggregateType;

    // Full event payload as JSON — immutable record
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    // When the event occurred (from payload)
    @Column(name = "occurred_at")
    private LocalDateTime occurredAt;

    // When this record was written
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;
}
