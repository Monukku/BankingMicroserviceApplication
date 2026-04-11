package com.rewabank.auth.repository;

import com.rewabank.auth.entity.OtpRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpRecordRepository extends JpaRepository<OtpRecord, UUID> {

    @Query("""
        SELECT o FROM OtpRecord o
        WHERE o.userId = :userId
        AND o.purpose = :purpose
        AND o.used = false
        AND o.expiresAt > :now
        ORDER BY o.createdAt DESC
        """)
    Optional<OtpRecord> findValidOtp(
            UUID userId,
            OtpRecord.OtpPurpose purpose,
            LocalDateTime now
    );

    @Modifying
    @Query("UPDATE OtpRecord o SET o.used = true WHERE o.userId = :userId AND o.purpose = :purpose AND o.used = false")
    void invalidateAllOtps(UUID userId, OtpRecord.OtpPurpose purpose);
}
