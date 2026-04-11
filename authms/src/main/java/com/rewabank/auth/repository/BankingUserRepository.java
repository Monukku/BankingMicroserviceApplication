package com.rewabank.auth.repository;

import com.rewabank.auth.entity.BankingUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankingUserRepository extends JpaRepository<BankingUser, UUID> {

    Optional<BankingUser> findByEmailAndDeletedAtIsNull(String email);

    Optional<BankingUser> findByKeycloakUserIdAndDeletedAtIsNull(String keycloakUserId);

    Optional<BankingUser> findByMobileNumberAndDeletedAtIsNull(String mobileNumber);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    boolean existsByMobileNumberAndDeletedAtIsNull(String mobileNumber);

    @Modifying
    @Query("UPDATE BankingUser u SET u.failedOtpAttempts = 0, u.otpLockedUntil = null WHERE u.id = :id")
    void resetOtpLock(UUID id);
}
