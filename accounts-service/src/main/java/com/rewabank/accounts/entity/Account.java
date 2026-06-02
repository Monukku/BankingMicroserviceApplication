package com.rewabank.accounts.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // 12-digit account number — generated via SecureRandom
    @Column(name = "account_number", nullable = false, unique = true, length = 12)
    private String accountNumber;

    @Column(name = "keycloak_user_id", nullable = false)
    private String keycloakUserId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    // ── State machine: PENDING → ACTIVE → DORMANT → FROZEN → CLOSED ──
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AccountStatus status = AccountStatus.PENDING;

    // BigDecimal ONLY — never float or double for money
    @Column(nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "minimum_balance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal minimumBalance = BigDecimal.ZERO;

    @Column(name = "interest_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal interestRate = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "branch_code", length = 10)
    private String branchCode;

    @Column(name = "ifsc_code", length = 11)
    private String ifscCode;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "frozen_at")
    private LocalDateTime frozenAt;

    @Column(name = "frozen_reason")
    private String frozenReason;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "last_transaction_at")
    private LocalDateTime lastTransactionAt;

    // Soft delete
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── State machine allowed transitions ──────────────────────────────
    public boolean canActivate() {
        return status == AccountStatus.PENDING;
    }

    public boolean canFreeze() {
        return status == AccountStatus.ACTIVE ||
                status == AccountStatus.DORMANT;
    }

    public boolean canUnfreeze() {
        return status == AccountStatus.FROZEN;
    }

    public boolean canClose() {
        return status == AccountStatus.ACTIVE ||
                status == AccountStatus.DORMANT ||
                status == AccountStatus.FROZEN;
    }

    public boolean canTransact() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public enum AccountStatus {
        PENDING,    // created, waiting for KYC
        ACTIVE,     // KYC verified, can transact
        DORMANT,    // no transaction for 12 months
        FROZEN,     // blocked due to fraud / compliance
        CLOSED      // permanently closed
    }

    public enum AccountType {
        SAVINGS,
        CURRENT,
        FIXED_DEPOSIT,
        RECURRING_DEPOSIT,
        SALARY
    }
}
