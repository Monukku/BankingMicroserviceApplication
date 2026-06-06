package com.rewabank.accounts.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Outbox pattern — guaranteed at-least-once Kafka delivery.
 * Event is saved in same DB transaction as business data.
 * OutboxScheduler polls and publishes to Kafka.
 */
@Entity
@Table(name = "outbox_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;    // accountId

    @Column(name = "aggregate_type", nullable = false)
    @Builder.Default
    private String aggregateType = "Account";

    @Column(name = "event_type", nullable = false)
    private String eventType;      // e.g. ACCOUNT_CREATED

    @Column(name = "topic", nullable = false)
    private String topic;          // Kafka topic

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;        // JSON string

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum OutboxStatus {
        PENDING,
        PUBLISHED,
        FAILED
    }
}
