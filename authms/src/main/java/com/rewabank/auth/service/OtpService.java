package com.rewabank.auth.service;

import com.rewabank.auth.dto.OtpRequest;
import com.rewabank.auth.dto.OtpResponse;
import com.rewabank.auth.entity.BankingUser;
import com.rewabank.auth.entity.OtpRecord;
import com.rewabank.auth.exception.AuthException;
import com.rewabank.auth.repository.BankingUserRepository;
import com.rewabank.auth.repository.OtpRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final OtpRecordRepository    otpRecordRepository;
    private final BankingUserRepository  bankingUserRepository;
    private final StringRedisTemplate    redisTemplate;

    private final BCryptPasswordEncoder  passwordEncoder = new BCryptPasswordEncoder(10);
    private final SecureRandom           secureRandom    = new SecureRandom();

    private static final int OTP_EXPIRY_SECONDS  = 300;   // 5 minutes
    private static final int MAX_OTP_ATTEMPTS    = 3;
    private static final String OTP_LOCK_PREFIX  = "otp:lock:";
    private static final String OTP_RATE_PREFIX  = "otp:rate:";

    @Value("${otp.expiry-seconds:300}")
    private int otpExpirySeconds;

    @Transactional
    public OtpResponse generateOtp(String keycloakUserId, OtpRequest request) {
        BankingUser user = bankingUserRepository
                .findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId)
                .orElseThrow(() -> new AuthException("AUTH_003", "User not found"));

        // Rate limit: max 3 OTPs per 10 minutes per purpose
        String rateKey = OTP_RATE_PREFIX + user.getId() + ":" + request.purpose();
        String rateCount = redisTemplate.opsForValue().get(rateKey);
        if (rateCount != null && Integer.parseInt(rateCount) >= 3) {
            throw new AuthException("AUTH_004", "Too many OTP requests. Please wait 10 minutes.");
        }

        // OTP lock check
        if (user.getOtpLockedUntil() != null &&
                user.getOtpLockedUntil().isAfter(LocalDateTime.now())) {
            throw new AuthException("AUTH_005", "Account temporarily locked due to too many failed OTP attempts");
        }

        // Invalidate previous unused OTPs for same purpose
        otpRecordRepository.invalidateAllOtps(user.getId(), request.purpose());

        // Generate 6-digit OTP via SecureRandom
        String otp     = String.format("%06d", secureRandom.nextInt(1_000_000));
        String otpHash = passwordEncoder.encode(otp);

        OtpRecord record = OtpRecord.builder()
                .userId(user.getId())
                .otpHash(otpHash)
                .purpose(request.purpose())
                .referenceId(request.referenceId())
                .expiresAt(LocalDateTime.now().plusSeconds(otpExpirySeconds))
                .build();
        otpRecordRepository.save(record);

        // Increment rate limit counter
        redisTemplate.opsForValue().increment(rateKey);
        redisTemplate.expire(rateKey, Duration.ofMinutes(10));

        // In production: send OTP via SMS/Email (Notifications MS handles this via Kafka)
        // Here we only log at DEBUG — never log OTP in production INFO/WARN
        log.debug("OTP generated for user {} purpose {}", user.getId(), request.purpose());

        return new OtpResponse(
                true,
                "OTP sent to your registered mobile number",
                maskMobile(user.getMobileNumber()),
                otpExpirySeconds
        );
    }

    @Transactional
    public boolean verifyOtp(String keycloakUserId, String otp, OtpRecord.OtpPurpose purpose) {
        BankingUser user = bankingUserRepository
                .findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId)
                .orElseThrow(() -> new AuthException("AUTH_003", "User not found"));

        OtpRecord record = otpRecordRepository
                .findValidOtp(user.getId(), purpose, LocalDateTime.now())
                .orElseThrow(() -> new AuthException("AUTH_006", "OTP expired or not found"));

        if (!passwordEncoder.matches(otp, record.getOtpHash())) {
            // Increment failed attempts
            user.setFailedOtpAttempts(user.getFailedOtpAttempts() + 1);
            if (user.getFailedOtpAttempts() >= MAX_OTP_ATTEMPTS) {
                user.setOtpLockedUntil(LocalDateTime.now().plusMinutes(15));
                log.warn("User {} locked for 15 minutes after {} failed OTP attempts",
                        user.getId(), MAX_OTP_ATTEMPTS);
            }
            bankingUserRepository.save(user);
            throw new AuthException("AUTH_007", "Invalid OTP");
        }

        // Mark OTP as used
        record.setUsed(true);
        otpRecordRepository.save(record);

        // Reset failed attempts on success
        bankingUserRepository.resetOtpLock(user.getId());

        log.info("OTP verified successfully for user {} purpose {}", user.getId(), purpose);
        return true;
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 10) return "XXXXXXXXXX";
        return mobile.substring(0, 3) + "XXXXXXX" + mobile.substring(mobile.length() - 3);
    }
}
