package com.rewabank.cards.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cards")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "keycloak_user_id", nullable = false)
    private String keycloakUserId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    // AES-256-GCM encrypted — never store plain card number
    @Column(name = "card_number_encrypted", nullable = false)
    private String cardNumberEncrypted;

    // Last 4 digits only — for display
    @Column(name = "card_last_four", nullable = false, length = 4)
    private String cardLastFour;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    private CardType cardType;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_network", nullable = false)
    private CardNetwork cardNetwork;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CardStatus status = CardStatus.PENDING;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    // CVV stored hashed — never plain
    @Column(name = "cvv_hash", nullable = false)
    private String cvvHash;

    @Column(name = "name_on_card", nullable = false)
    private String nameOnCard;

    @Column(name = "block_reason")
    private String blockReason;

    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;

    @Column(name = "blocked_by")
    private String blockedBy;       // keycloakUserId who blocked

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "expiry_alert_sent")
    @Builder.Default
    private Boolean expiryAlertSent = false;

    @Column(name = "daily_limit")
    @Builder.Default
    private BigDecimal dailyLimit = new BigDecimal("100000");

    @Column(name = "monthly_limit")
    @Builder.Default
    private BigDecimal monthlyLimit = new BigDecimal("500000");

    @Column(name = "international_enabled")
    @Builder.Default
    private Boolean internationalEnabled = true;

    @Column(name = "online_enabled")
    @Builder.Default
    private Boolean onlineEnabled = true;

    @Column(name = "customer_id")
    private UUID customerId;

    // Caller-supplied idempotency key — duplicate requests return the original card
    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum CardType {
        DEBIT, CREDIT, PREPAID
    }

    public enum CardNetwork {
        VISA, MASTERCARD, RUPAY
    }

    public enum CardStatus {
        PENDING,    // issued, not yet activated
        ACTIVE,     // active and can transact
        BLOCKED,    // temporarily blocked
        CANCELLED,  // permanently cancelled
        EXPIRED     // past expiry date
    }

    public boolean canBlock() {
        return status == CardStatus.ACTIVE;
    }

    public boolean canUnblock() {
        return status == CardStatus.BLOCKED;
    }

    public boolean canActivate() {
        return status == CardStatus.PENDING;
    }

    public boolean isExpired() {
        return expiryDate.isBefore(LocalDate.now());
    }

    public boolean isActive() {
        return status == CardStatus.ACTIVE && !isExpired();
    }
}