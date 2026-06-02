package com.rewabank.transactions.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "beneficiaries")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Beneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "keycloak_user_id", nullable = false)
    private String keycloakUserId;

    @Column(name = "beneficiary_account_number", nullable = false, length = 12)
    private String beneficiaryAccountNumber;

    @Column(name = "beneficiary_name", nullable = false)
    private String beneficiaryName;

    @Column(name = "beneficiary_bank", length = 100)
    private String beneficiaryBank;

    @Column(name = "ifsc_code", length = 11)
    private String ifscCode;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // 24hr cooling period — cannot transact until this passes
    @Column(name = "cooling_period_ends_at", nullable = false)
    private LocalDateTime coolingPeriodEndsAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public boolean isCoolingPeriodOver() {
        return LocalDateTime.now().isAfter(coolingPeriodEndsAt);
    }
}
