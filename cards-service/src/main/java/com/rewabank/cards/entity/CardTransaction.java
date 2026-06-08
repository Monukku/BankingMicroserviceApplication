package com.rewabank.cards.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

// ── Card Transaction ─────────────────────────────────────────────────────────
@Entity
@Table(name = "card_transactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CardTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "merchant_name", length = 255)
    private String merchantName;

    @Column(name = "merchant_category", length = 100)
    private String merchantCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private Channel channel;

    @Column(name = "is_international", nullable = false)
    @Builder.Default
    private Boolean isInternational = false;

    @Column(name = "fraud_score")
    private Integer fraudScore;

    @Column(name = "declined_reason", length = 255)
    private String declinedReason;

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "authorized_at")
    private LocalDateTime authorizedAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "declined_at")
    private LocalDateTime declinedAt;

    @Column(name = "reversed_at")
    private LocalDateTime reversedAt;

    @Column(name = "reverse_reason", length = 255)
    private String reverseReason;

    @Column(name = "description", length = 500)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum TransactionType {
        PURCHASE, CASH_WITHDRAWAL, REFUND, EMI_CONVERSION,
        CREDIT_PAYMENT, REVERSAL, ONLINE
    }

    public enum TransactionStatus { PENDING, AUTHORIZED, SETTLED, DECLINED, REVERSED }

    public enum Channel { ONLINE, POS, ATM, CONTACTLESS }
}