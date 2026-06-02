package com.rewabank.fraud.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "fraud_alerts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FraudAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "keycloak_user_id")
    private String keycloakUserId;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "fraud_score", nullable = false)
    private Integer fraudScore;

    @Column(nullable = false, length = 10)
    private String action;              // FLAG or BLOCK

    @Column(name = "triggered_rules", columnDefinition = "TEXT")
    private String triggeredRules;      // JSON list of triggered rules

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AlertStatus status = AlertStatus.OPEN;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolution_notes")
    private String resolutionNotes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum AlertStatus {
        OPEN, RESOLVED, FALSE_POSITIVE
    }
}
