package com.rewabank.loans.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

// ── EMI Schedule ─────────────────────────────────────────────────────────────
@Entity
@Table(name = "emi_schedules")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmiSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "loan_id", nullable = false)
    private UUID loanId;

    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "emi_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal emiAmount;

    @Column(name = "principal_part", nullable = false, precision = 15, scale = 2)
    private BigDecimal principalPart;

    @Column(name = "interest_part", nullable = false, precision = 15, scale = 2)
    private BigDecimal interestPart;

    @Column(name = "outstanding_after", nullable = false, precision = 15, scale = 2)
    private BigDecimal outstandingAfter;

    @Column(name = "penalty_amount", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal penaltyAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private EmiStatus status = EmiStatus.PENDING;

    @Column(name = "paid_amount", precision = 15, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum EmiStatus {
        PENDING, PAID, OVERDUE, PARTIALLY_PAID, WAIVED
    }
}