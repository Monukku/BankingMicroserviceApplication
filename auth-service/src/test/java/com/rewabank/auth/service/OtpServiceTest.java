package com.rewabank.auth.service;

import com.rewabank.auth.dto.OtpRequest;
import com.rewabank.auth.dto.OtpResponse;
import com.rewabank.auth.entity.BankingUser;
import com.rewabank.auth.entity.OtpRecord;
import com.rewabank.auth.exception.AuthException;
import com.rewabank.auth.repository.BankingUserRepository;
import com.rewabank.auth.repository.OtpRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtpServiceTest {

    @Mock private OtpRecordRepository    otpRecordRepository;
    @Mock private BankingUserRepository  bankingUserRepository;
    @Mock private StringRedisTemplate    redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    // Cost 4 for fast tests; BCrypt matches() reads cost from the hash, so any instance verifies any hash
    @Spy  private BCryptPasswordEncoder  passwordEncoder = new BCryptPasswordEncoder(4);

    @InjectMocks
    private OtpService otpService;

    // BCrypt(4) for speed — BCrypt cost is embedded in the hash, matches() still works
    private static final String VALID_OTP      = "123456";
    private static final String VALID_OTP_HASH =
            new BCryptPasswordEncoder(4).encode(VALID_OTP);

    private String keycloakUserId;
    private BankingUser user;
    private OtpRequest  transferRequest;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(otpService, "otpExpirySeconds", 300);
        ReflectionTestUtils.setField(otpService, "maxOtpAttempts", 3);
        ReflectionTestUtils.setField(otpService, "rateWindowMinutes", 10);
        ReflectionTestUtils.setField(otpService, "lockMinutes", 15);

        keycloakUserId = "kc-user-001";
        user = BankingUser.builder()
                .id(UUID.randomUUID())
                .keycloakUserId(keycloakUserId)
                .email("rahul@example.com")
                .mobileNumber("+919876543210")
                .fullName("Rahul Sharma")
                .failedOtpAttempts(0)
                .build();

        transferRequest = new OtpRequest(OtpRecord.OtpPurpose.TRANSFER, "txn-001");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ── generateOtp: happy path ───────────────────────────────────────────────

    @Test
    void generateOtp_ShouldReturnOtpResponse_WhenUserExistsAndNotRateLimited() {
        when(bankingUserRepository.findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId))
                .thenReturn(Optional.of(user));
        when(valueOperations.get(anyString())).thenReturn(null); // not rate limited

        OtpResponse response = otpService.generateOtp(keycloakUserId, transferRequest);

        assertTrue(response.success());
        assertNotNull(response.maskedMobile());
        assertEquals(300, response.expiresInSeconds());
        verify(otpRecordRepository).save(any(OtpRecord.class));
    }

    @Test
    void generateOtp_ShouldMaskMobileNumber_InResponse() {
        when(bankingUserRepository.findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId))
                .thenReturn(Optional.of(user));
        when(valueOperations.get(anyString())).thenReturn(null);

        OtpResponse response = otpService.generateOtp(keycloakUserId, transferRequest);

        // +919876543210 → +91XXXXXXX210
        assertEquals("+91XXXXXXX210", response.maskedMobile());
    }

    @Test
    void generateOtp_ShouldInvalidatePreviousOtps_BeforeSavingNew() {
        when(bankingUserRepository.findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId))
                .thenReturn(Optional.of(user));
        when(valueOperations.get(anyString())).thenReturn(null);

        otpService.generateOtp(keycloakUserId, transferRequest);

        verify(otpRecordRepository).invalidateAllOtps(user.getId(), OtpRecord.OtpPurpose.TRANSFER);
    }

    @Test
    void generateOtp_ShouldSaveOtpRecordWithCorrectPurposeAndReferenceId() {
        when(bankingUserRepository.findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId))
                .thenReturn(Optional.of(user));
        when(valueOperations.get(anyString())).thenReturn(null);

        otpService.generateOtp(keycloakUserId, transferRequest);

        ArgumentCaptor<OtpRecord> captor = ArgumentCaptor.forClass(OtpRecord.class);
        verify(otpRecordRepository).save(captor.capture());
        assertEquals(OtpRecord.OtpPurpose.TRANSFER, captor.getValue().getPurpose());
        assertEquals("txn-001", captor.getValue().getReferenceId());
        assertEquals(user.getId(), captor.getValue().getUserId());
        assertNotNull(captor.getValue().getOtpHash());
        assertNotNull(captor.getValue().getExpiresAt());
    }

    @Test
    void generateOtp_ShouldIncrementRateLimitCounter_WithTenMinuteTtl() {
        when(bankingUserRepository.findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId))
                .thenReturn(Optional.of(user));
        when(valueOperations.get(anyString())).thenReturn(null);

        otpService.generateOtp(keycloakUserId, transferRequest);

        verify(valueOperations).increment(contains("otp:rate:"));
        verify(redisTemplate).expire(contains("otp:rate:"), eq(Duration.ofMinutes(10)));
    }

    // ── generateOtp: error cases ──────────────────────────────────────────────

    @Test
    void generateOtp_ShouldThrowAuthException_WhenUserNotFound() {
        when(bankingUserRepository.findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId))
                .thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> otpService.generateOtp(keycloakUserId, transferRequest));
        assertEquals("AUTH_003", ex.getErrorCode());
    }

    @Test
    void generateOtp_ShouldThrowAuthException_WhenRateLimitExceeded() {
        when(bankingUserRepository.findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId))
                .thenReturn(Optional.of(user));
        when(valueOperations.get(anyString())).thenReturn("3"); // at limit

        AuthException ex = assertThrows(AuthException.class,
                () -> otpService.generateOtp(keycloakUserId, transferRequest));
        assertEquals("AUTH_004", ex.getErrorCode());
        verify(otpRecordRepository, never()).save(any());
    }

    @Test
    void generateOtp_ShouldNotRateLimit_WhenCountIsTwo() {
        when(bankingUserRepository.findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId))
                .thenReturn(Optional.of(user));
        when(valueOperations.get(anyString())).thenReturn("2"); // below limit

        assertDoesNotThrow(() -> otpService.generateOtp(keycloakUserId, transferRequest));
    }

    @Test
    void generateOtp_ShouldThrowAuthException_WhenAccountIsOtpLocked() {
        user.setOtpLockedUntil(LocalDateTime.now().plusMinutes(10)); // locked for 10 min
        when(bankingUserRepository.findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId))
                .thenReturn(Optional.of(user));
        when(valueOperations.get(anyString())).thenReturn(null);

        AuthException ex = assertThrows(AuthException.class,
                () -> otpService.generateOtp(keycloakUserId, transferRequest));
        assertEquals("AUTH_005", ex.getErrorCode());
    }

    @Test
    void generateOtp_ShouldNotThrow_WhenOtpLockHasExpired() {
        user.setOtpLockedUntil(LocalDateTime.now().minusMinutes(1)); // lock expired
        when(bankingUserRepository.findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId))
                .thenReturn(Optional.of(user));
        when(valueOperations.get(anyString())).thenReturn(null);

        assertDoesNotThrow(() -> otpService.generateOtp(keycloakUserId, transferRequest));
    }

    // ── verifyOtp: happy path ─────────────────────────────────────────────────

    @Test
    void verifyOtp_ShouldReturnTrue_WhenOtpIsCorrect() {
        OtpRecord record = buildOtpRecord(VALID_OTP_HASH, false);
        when(bankingUserRepository.findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId))
                .thenReturn(Optional.of(user));
        when(otpRecordRepository.findValidOtp(eq(user.getId()),
                eq(OtpRecord.OtpPurpose.TRANSFER), any(LocalDateTime.class)))
                .thenReturn(Optional.of(record));

        boolean result = otpService.verifyOtp(
                keycloakUserId, VALID_OTP, OtpRecord.OtpPurpose.TRANSFER);

        assertTrue(result);
    }

    @Test
    void verifyOtp_ShouldMarkOtpAsUsed_OnSuccess() {
        OtpRecord record = buildOtpRecord(VALID_OTP_HASH, false);
        when(bankingUserRepository.findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId))
                .thenReturn(Optional.of(user));
        when(otpRecordRepository.findValidOtp(any(), any(), any()))
                .thenReturn(Optional.of(record));

        otpService.verifyOtp(keycloakUserId, VALID_OTP, OtpRecord.OtpPurpose.TRANSFER);

        assertTrue(record.getUsed());
        verify(otpRecordRepository).save(record);
    }

    @Test
    void verifyOtp_ShouldResetFailedAttempts_OnSuccess() {
        user.setFailedOtpAttempts(2);
        OtpRecord record = buildOtpRecord(VALID_OTP_HASH, false);
        when(bankingUserRepository.findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId))
                .thenReturn(Optional.of(user));
        when(otpRecordRepository.findValidOtp(any(), any(), any()))
                .thenReturn(Optional.of(record));

        otpService.verifyOtp(keycloakUserId, VALID_OTP, OtpRecord.OtpPurpose.TRANSFER);

        verify(bankingUserRepository).resetOtpLock(user.getId());
    }

    // ── verifyOtp: error cases ────────────────────────────────────────────────

    @Test
    void verifyOtp_ShouldThrowAuthException_WhenUserNotFound() {
        when(bankingUserRepository.findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId))
                .thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> otpService.verifyOtp(keycloakUserId, VALID_OTP, OtpRecord.OtpPurpose.TRANSFER));
        assertEquals("AUTH_003", ex.getErrorCode());
    }

    @Test
    void verifyOtp_ShouldThrowAuthException_WhenOtpExpiredOrNotFound() {
        when(bankingUserRepository.findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId))
                .thenReturn(Optional.of(user));
        when(otpRecordRepository.findValidOtp(any(), any(), any()))
                .thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> otpService.verifyOtp(keycloakUserId, VALID_OTP, OtpRecord.OtpPurpose.TRANSFER));
        assertEquals("AUTH_006", ex.getErrorCode());
    }

    @Test
    void verifyOtp_ShouldThrowAuthException_WhenOtpDoesNotMatch() {
        OtpRecord record = buildOtpRecord(VALID_OTP_HASH, false);
        when(bankingUserRepository.findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId))
                .thenReturn(Optional.of(user));
        when(otpRecordRepository.findValidOtp(any(), any(), any()))
                .thenReturn(Optional.of(record));

        AuthException ex = assertThrows(AuthException.class,
                () -> otpService.verifyOtp(keycloakUserId, "999999", OtpRecord.OtpPurpose.TRANSFER));
        assertEquals("AUTH_007", ex.getErrorCode());
    }

    @Test
    void verifyOtp_ShouldIncrementFailedAttempts_WhenOtpWrong() {
        user.setFailedOtpAttempts(0);
        OtpRecord record = buildOtpRecord(VALID_OTP_HASH, false);
        when(bankingUserRepository.findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId))
                .thenReturn(Optional.of(user));
        when(otpRecordRepository.findValidOtp(any(), any(), any()))
                .thenReturn(Optional.of(record));

        assertThrows(AuthException.class,
                () -> otpService.verifyOtp(keycloakUserId, "000000", OtpRecord.OtpPurpose.TRANSFER));

        assertEquals(1, user.getFailedOtpAttempts());
        verify(bankingUserRepository).save(user);
    }

    @Test
    void verifyOtp_ShouldLockUser_WhenFailedAttemptsReachThree() {
        user.setFailedOtpAttempts(2); // one more wrong attempt will lock
        OtpRecord record = buildOtpRecord(VALID_OTP_HASH, false);
        when(bankingUserRepository.findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId))
                .thenReturn(Optional.of(user));
        when(otpRecordRepository.findValidOtp(any(), any(), any()))
                .thenReturn(Optional.of(record));

        assertThrows(AuthException.class,
                () -> otpService.verifyOtp(keycloakUserId, "000000", OtpRecord.OtpPurpose.TRANSFER));

        assertEquals(3, user.getFailedOtpAttempts());
        assertNotNull(user.getOtpLockedUntil(),
                "Account should be locked after 3 failed attempts");
        assertTrue(user.getOtpLockedUntil().isAfter(LocalDateTime.now()));
    }

    @Test
    void verifyOtp_ShouldNotLock_WhenFailedAttemptsAreBelowThree() {
        user.setFailedOtpAttempts(1);
        OtpRecord record = buildOtpRecord(VALID_OTP_HASH, false);
        when(bankingUserRepository.findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId))
                .thenReturn(Optional.of(user));
        when(otpRecordRepository.findValidOtp(any(), any(), any()))
                .thenReturn(Optional.of(record));

        assertThrows(AuthException.class,
                () -> otpService.verifyOtp(keycloakUserId, "000000", OtpRecord.OtpPurpose.TRANSFER));

        assertNull(user.getOtpLockedUntil(),
                "Account should NOT be locked after only 2 failed attempts");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private OtpRecord buildOtpRecord(String hash, boolean used) {
        return OtpRecord.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .otpHash(hash)
                .purpose(OtpRecord.OtpPurpose.TRANSFER)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .used(used)
                .build();
    }
}
