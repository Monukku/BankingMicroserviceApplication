package com.rewabank.loans.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_applications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "keycloak_user_id", nullable = false)
    private String keycloakUserId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_type", nullable = false)
    private LoanType loanType;

    // BigDecimal only — never float
    @Column(name = "requested_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal requestedAmount;

    @Column(name = "approved_amount", precision = 19, scale = 4)
    private BigDecimal approvedAmount;

    @Column(name = "interest_rate", precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "tenure_months", nullable = false)
    private Integer tenureMonths;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LoanStatus status = LoanStatus.APPLIED;

    @Column(name = "purpose", length = 500)
    private String purpose;

    @Column(name = "reviewer_id")
    private String reviewerId;

    @Column(name = "reviewer_role", length = 30)
    private String reviewerRole;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "review_notes", length = 500)
    private String reviewNotes;

    @Column(name = "fraud_score")
    private Integer fraudScore;

    @Column(name = "fraud_action", length = 10)
    private String fraudAction;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "disbursed_at")
    private LocalDateTime disbursedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum LoanType {
        PERSONAL, HOME, VEHICLE, EDUCATION, BUSINESS, GOLD
    }

    public enum LoanStatus {
        APPLIED,
        UNDER_REVIEW,
        APPROVED,
        DISBURSED,
        REJECTED,
        CLOSED
    }

    // State machine checks
    public boolean canReview() {
        return status == LoanStatus.APPLIED;
    }

    public boolean canApprove() {
        return status == LoanStatus.APPLIED ||
                status == LoanStatus.UNDER_REVIEW;
    }

    public boolean canReject() {
        return status == LoanStatus.APPLIED ||
                status == LoanStatus.UNDER_REVIEW;
    }

    public boolean canDisburse() {
        return status == LoanStatus.APPROVED;
    }
}