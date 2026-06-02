package com.rewabank.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "banking_users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BankingUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "keycloak_user_id", nullable = false, unique = true)
    private String keycloakUserId;

    @Column(nullable = false, unique = true)
    private String email;

    // Stored masked: +91XXXXXXX890
    @Column(name = "mobile_number", nullable = false, unique = true, length = 15)
    private String mobileNumber;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "kyc_verified", nullable = false)
    @Builder.Default
    private Boolean kycVerified = false;

    @Column(name = "failed_otp_attempts", nullable = false)
    @Builder.Default
    private Integer failedOtpAttempts = 0;

    @Column(name = "otp_locked_until")
    private LocalDateTime otpLockedUntil;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt; // soft delete

    public enum UserStatus {
        ACTIVE, SUSPENDED, DELETED
    }
}
