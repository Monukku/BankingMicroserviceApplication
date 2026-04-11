package com.rewabank.customers.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "customers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // From Auth MS via bank.user.registered event
    @Column(name = "keycloak_user_id", nullable = false, unique = true)
    private String keycloakUserId;

    @Column(nullable = false)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "mobile_number", nullable = false)
    private String mobileNumber;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    // AES-256 encrypted — stored as base64 ciphertext
    @Column(name = "aadhaar_number_encrypted")
    private String aadhaarNumberEncrypted;

    // AES-256 encrypted
    @Column(name = "pan_number_encrypted")
    private String panNumberEncrypted;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false)
    @Builder.Default
    private KycStatus kycStatus = KycStatus.NOT_SUBMITTED;

    @Column(name = "kyc_verified_at")
    private LocalDateTime kycVerifiedAt;

    @Column(name = "kyc_verified_by")
    private String kycVerifiedBy;   // keycloakUserId of RM/Branch Manager

    @Column(name = "kyc_rejection_reason")
    private String kycRejectionReason;

    @Column(name = "kyc_submitted_at")
    private LocalDateTime kycSubmittedAt;

    @OneToOne(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Address address;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;   // soft delete

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum KycStatus {
        NOT_SUBMITTED,
        SUBMITTED,
        UNDER_REVIEW,
        VERIFIED,
        REJECTED
    }

    public boolean isKycVerified() {
        return KycStatus.VERIFIED.equals(this.kycStatus);
    }
}
