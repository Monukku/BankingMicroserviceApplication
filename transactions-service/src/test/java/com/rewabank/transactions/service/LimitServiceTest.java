package com.rewabank.transactions.service;

import com.rewabank.transactions.entity.DailyLimit;
import com.rewabank.transactions.exception.TransactionException;
import com.rewabank.transactions.repository.DailyLimitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LimitServiceTest {

    @Mock private DailyLimitRepository dailyLimitRepository;

    @InjectMocks private LimitService limitService;

    private static final String USER_ID = "kc-user-001";

    private DailyLimit freshLimit() {
        return DailyLimit.builder()
                .id(UUID.randomUUID())
                .keycloakUserId(USER_ID)
                .limitDate(LocalDate.now())
                .dailyLimit(new BigDecimal("100000.0000"))
                .usedAmount(BigDecimal.ZERO)
                .build();
    }

    @BeforeEach
    void setUp() {
        when(dailyLimitRepository.save(any(DailyLimit.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(dailyLimitRepository.saveAndFlush(any(DailyLimit.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    // ── per-transaction limit by role ─────────────────────────────────────────

    @Test
    void validateAndConsumeLimit_ShouldPass_WhenCustomerWithinOneL() {
        when(dailyLimitRepository.findWithLockByKeycloakUserIdAndLimitDate(eq(USER_ID), any()))
                .thenReturn(Optional.of(freshLimit()));

        assertDoesNotThrow(() -> limitService.validateAndConsumeLimit(
                USER_ID, new BigDecimal("50000.00"), "CUSTOMER"));
    }

    @Test
    void validateAndConsumeLimit_ShouldThrow_WhenCustomerExceedsOneLakh() {
        TransactionException ex = assertThrows(TransactionException.class,
                () -> limitService.validateAndConsumeLimit(
                        USER_ID, new BigDecimal("100001.00"), "CUSTOMER"));
        assertEquals("TXN_002", ex.getErrorCode());
    }

    @Test
    void validateAndConsumeLimit_ShouldPass_WhenBranchManagerWithinTenLakh() {
        when(dailyLimitRepository.findWithLockByKeycloakUserIdAndLimitDate(eq(USER_ID), any()))
                .thenReturn(Optional.of(DailyLimit.builder()
                        .keycloakUserId(USER_ID)
                        .limitDate(LocalDate.now())
                        .dailyLimit(new BigDecimal("1000000.0000"))
                        .usedAmount(BigDecimal.ZERO)
                        .build()));

        assertDoesNotThrow(() -> limitService.validateAndConsumeLimit(
                USER_ID, new BigDecimal("999999.00"), "BRANCH_MANAGER"));
    }

    @Test
    void validateAndConsumeLimit_ShouldThrow_WhenBranchManagerExceedsTenLakh() {
        TransactionException ex = assertThrows(TransactionException.class,
                () -> limitService.validateAndConsumeLimit(
                        USER_ID, new BigDecimal("1000001.00"), "BRANCH_MANAGER"));
        assertEquals("TXN_002", ex.getErrorCode());
    }

    @Test
    void validateAndConsumeLimit_ShouldApplyCustomerLimit_WhenRoleIsNull() {
        TransactionException ex = assertThrows(TransactionException.class,
                () -> limitService.validateAndConsumeLimit(
                        USER_ID, new BigDecimal("100001.00"), null));
        assertEquals("TXN_002", ex.getErrorCode());
    }

    @Test
    void validateAndConsumeLimit_ShouldApplyCustomerLimit_WhenRoleIsUnknown() {
        TransactionException ex = assertThrows(TransactionException.class,
                () -> limitService.validateAndConsumeLimit(
                        USER_ID, new BigDecimal("100001.00"), "JANITOR"));
        assertEquals("TXN_002", ex.getErrorCode());
    }

    @Test
    void validateAndConsumeLimit_ShouldApplyRmLimit_FiveL() {
        when(dailyLimitRepository.findWithLockByKeycloakUserIdAndLimitDate(eq(USER_ID), any()))
                .thenReturn(Optional.of(DailyLimit.builder()
                        .keycloakUserId(USER_ID)
                        .limitDate(LocalDate.now())
                        .dailyLimit(new BigDecimal("1000000.0000"))
                        .usedAmount(BigDecimal.ZERO)
                        .build()));

        assertDoesNotThrow(() -> limitService.validateAndConsumeLimit(
                USER_ID, new BigDecimal("499999.00"), "RELATIONSHIP_MANAGER"));

        TransactionException ex = assertThrows(TransactionException.class,
                () -> limitService.validateAndConsumeLimit(
                        USER_ID, new BigDecimal("500001.00"), "RELATIONSHIP_MANAGER"));
        assertEquals("TXN_002", ex.getErrorCode());
    }

    // ── daily limit ───────────────────────────────────────────────────────────

    @Test
    void validateAndConsumeLimit_ShouldThrow_WhenDailyLimitExceeded() {
        DailyLimit almostFull = DailyLimit.builder()
                .keycloakUserId(USER_ID)
                .limitDate(LocalDate.now())
                .dailyLimit(new BigDecimal("100000.0000"))
                .usedAmount(new BigDecimal("90000.0000"))  // only 10K remaining
                .build();
        when(dailyLimitRepository.findWithLockByKeycloakUserIdAndLimitDate(eq(USER_ID), any()))
                .thenReturn(Optional.of(almostFull));

        TransactionException ex = assertThrows(TransactionException.class,
                () -> limitService.validateAndConsumeLimit(
                        USER_ID, new BigDecimal("20000.00"), "CUSTOMER"));
        assertEquals("TXN_003", ex.getErrorCode());
    }

    @Test
    void validateAndConsumeLimit_ShouldConsumeFromDailyLimit_OnSuccess() {
        DailyLimit limit = freshLimit();
        when(dailyLimitRepository.findWithLockByKeycloakUserIdAndLimitDate(eq(USER_ID), any()))
                .thenReturn(Optional.of(limit));

        limitService.validateAndConsumeLimit(
                USER_ID, new BigDecimal("30000.00"), "CUSTOMER");

        ArgumentCaptor<DailyLimit> captor = ArgumentCaptor.forClass(DailyLimit.class);
        verify(dailyLimitRepository).save(captor.capture());
        assertEquals(0, new BigDecimal("30000.0000")
                .compareTo(captor.getValue().getUsedAmount()));
    }

    @Test
    void validateAndConsumeLimit_ShouldCreateNewDailyLimit_WhenNoneExistsToday() {
        when(dailyLimitRepository.findWithLockByKeycloakUserIdAndLimitDate(eq(USER_ID), any()))
                .thenReturn(Optional.empty());

        limitService.validateAndConsumeLimit(
                USER_ID, new BigDecimal("1000.00"), "CUSTOMER");

        // saveAndFlush to create, then save to consume
        verify(dailyLimitRepository).saveAndFlush(any(DailyLimit.class));
        verify(dailyLimitRepository).save(any(DailyLimit.class));
    }

    @Test
    void validateAndConsumeLimit_ShouldHandleConcurrentInsert_WhenUniqueConstraintViolated() {
        DailyLimit concurrentlyInserted = freshLimit();
        when(dailyLimitRepository.findWithLockByKeycloakUserIdAndLimitDate(eq(USER_ID), any()))
                .thenReturn(Optional.empty())         // first call: row doesn't exist yet
                .thenReturn(Optional.of(concurrentlyInserted)); // second call: after conflict
        when(dailyLimitRepository.saveAndFlush(any(DailyLimit.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint violation"));

        assertDoesNotThrow(() -> limitService.validateAndConsumeLimit(
                USER_ID, new BigDecimal("1000.00"), "CUSTOMER"));

        verify(dailyLimitRepository, times(2))
                .findWithLockByKeycloakUserIdAndLimitDate(eq(USER_ID), any());
        verify(dailyLimitRepository).save(any(DailyLimit.class));
    }

    @Test
    void validateAndConsumeLimit_ShouldPassExactlyAtDailyLimit() {
        DailyLimit limit = freshLimit();
        when(dailyLimitRepository.findWithLockByKeycloakUserIdAndLimitDate(eq(USER_ID), any()))
                .thenReturn(Optional.of(limit));

        // 100000 exactly equals dailyLimit — should pass (hasCapacity uses <=)
        assertDoesNotThrow(() -> limitService.validateAndConsumeLimit(
                USER_ID, new BigDecimal("100000.00"), "CUSTOMER"));
    }

    // ── DailyLimit entity helpers ─────────────────────────────────────────────

    @Test
    void dailyLimit_HasCapacity_ShouldReturnTrue_WhenUnderLimit() {
        DailyLimit limit = freshLimit();
        assertTrue(limit.hasCapacity(new BigDecimal("50000.00")));
    }

    @Test
    void dailyLimit_HasCapacity_ShouldReturnFalse_WhenOverLimit() {
        DailyLimit limit = DailyLimit.builder()
                .dailyLimit(new BigDecimal("100000.0000"))
                .usedAmount(new BigDecimal("80000.0000"))
                .build();
        assertFalse(limit.hasCapacity(new BigDecimal("30000.00")));
    }

    @Test
    void dailyLimit_RemainingLimit_ShouldBeCorrect() {
        DailyLimit limit = DailyLimit.builder()
                .dailyLimit(new BigDecimal("100000.0000"))
                .usedAmount(new BigDecimal("40000.0000"))
                .build();
        assertEquals(0, new BigDecimal("60000.0000")
                .compareTo(limit.remainingLimit()));
    }
}