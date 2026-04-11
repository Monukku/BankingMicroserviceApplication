package com.rewabank.transactions.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Idempotency key — client-provided, 24hr uniqueness
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "keycloak_user_id", nullable = false)
    private String keycloakUserId;

    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId;

    @Column(name = "destination_account_id", nullable = false)
    private UUID destinationAccountId;

    @Column(name = "source_account_number", nullable = false, length = 12)
    private String sourceAccountNumber;

    @Column(name = "destination_account_number", nullable = false, length = 12)
    private String destinationAccountNumber;

    // BigDecimal ONLY — never float or double
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.INITIATED;

    @Column(length = 500)
    private String remarks;

    @Column(name = "reference_number", unique = true)
    private String referenceNumber;      // RBI reference

    // Fraud score captured at time of transaction
    @Column(name = "fraud_score")
    private Integer fraudScore;

    @Column(name = "fraud_action", length = 10)
    private String fraudAction;          // APPROVE / FLAG / BLOCK

    // Saga state tracking
    @Column(name = "debit_completed")
    @Builder.Default
    private Boolean debitCompleted = false;

    @Column(name = "credit_completed")
    @Builder.Default
    private Boolean creditCompleted = false;

    @Column(name = "reversal_reason")
    private String reversalReason;

    @Column(name = "reversed_at")
    private LocalDateTime reversedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "device_id")
    private String deviceId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum TransactionType {
        TRANSFER,           // account to account
        NEFT,
        RTGS,
        IMPS,
        UPI,
        BILL_PAYMENT,
        REVERSAL
    }

    public enum TransactionStatus {
        INITIATED,          // created, fraud check pending
        FRAUD_CHECK,        // fraud check in progress
        PROCESSING,         // debit in progress
        COMPLETED,          // both debit + credit done
        FAILED,             // any step failed
        REVERSED,           // saga compensated
        BLOCKED             // blocked by fraud
    }
}
