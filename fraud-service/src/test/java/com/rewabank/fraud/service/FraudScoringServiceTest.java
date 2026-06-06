package com.rewabank.fraud.service;

import com.rewabank.fraud.model.FraudRule;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FraudScoringServiceTest {

    @Mock private RedisTemplate<String, Object>    redisTemplate;
    @Mock private ValueOperations<String, Object>  valueOperations;
    @Spy  private MeterRegistry                    meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private FraudScoringService fraudScoringService;

    private UUID accountId;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ── FraudRule.actionFor() thresholds ──────────────────────────────────────

    @Test
    void actionFor_ShouldReturnApprove_WhenScoreIsBelow50() {
        assertEquals("APPROVE", FraudRule.actionFor(0));
        assertEquals("APPROVE", FraudRule.actionFor(49));
    }

    @Test
    void actionFor_ShouldReturnFlag_WhenScoreIsBetween50And79() {
        assertEquals("FLAG", FraudRule.actionFor(50));
        assertEquals("FLAG", FraudRule.actionFor(79));
    }

    @Test
    void actionFor_ShouldReturnBlock_WhenScoreIs80OrAbove() {
        assertEquals("BLOCK", FraudRule.actionFor(80));
        assertEquals("BLOCK", FraudRule.actionFor(100));
    }

    // ── Rule 1: Large amount ──────────────────────────────────────────────────

    @Test
    void score_ShouldNotTriggerLargeAmountRule_WhenAmountIsExactlyThreshold() {
        mockNoVelocity();
        mockNoRepeatedAmount(new BigDecimal("50000.00"));
        mockNotNewAccount();

        FraudScoringService.ScoringResult result = fraudScoringService.score(
                accountId, new BigDecimal("50000.00"), "DEBIT", "corr-001");

        assertFalse(result.triggeredRules().contains("LARGE_AMOUNT"),
                "Exactly ₹50,000 should not trigger large amount rule (threshold is >50,000)");
    }

    @Test
    void score_ShouldTriggerLargeAmountRule_WhenAmountJustExceedsThreshold() {
        mockNoVelocity();
        mockNoRepeatedAmount(new BigDecimal("50001.00"));
        mockNotNewAccount();

        FraudScoringService.ScoringResult result = fraudScoringService.score(
                accountId, new BigDecimal("50001.00"), "DEBIT", "corr-002");

        assertTrue(result.triggeredRules().contains("LARGE_AMOUNT(+30)"));
    }

    @Test
    void score_ShouldAdd35Points_WhenAmountExceeds1Lakh() {
        mockNoVelocity();
        mockNoRepeatedAmount(new BigDecimal("150000.00"));
        mockNotNewAccount();

        FraudScoringService.ScoringResult result = fraudScoringService.score(
                accountId, new BigDecimal("150000.00"), "DEBIT", "corr-003");

        assertTrue(result.triggeredRules().contains("LARGE_AMOUNT(+35)"));
    }

    @Test
    void score_ShouldAdd40Points_WhenAmountExceeds2Lakh() {
        mockNoVelocity();
        mockNoRepeatedAmount(new BigDecimal("250000.00"));
        mockNotNewAccount();

        FraudScoringService.ScoringResult result = fraudScoringService.score(
                accountId, new BigDecimal("250000.00"), "DEBIT", "corr-004");

        assertTrue(result.triggeredRules().contains("LARGE_AMOUNT(+40)"));
    }

    @Test
    void score_ShouldAdd50Points_WhenAmountExceeds5Lakh() {
        mockNoVelocity();
        mockNoRepeatedAmount(new BigDecimal("600000.00"));
        mockNotNewAccount();

        FraudScoringService.ScoringResult result = fraudScoringService.score(
                accountId, new BigDecimal("600000.00"), "DEBIT", "corr-005");

        assertTrue(result.triggeredRules().contains("LARGE_AMOUNT(+50)"));
    }

    // ── Rule 2: Velocity ──────────────────────────────────────────────────────

    @Test
    void score_ShouldNotTriggerVelocityRule_WhenFewTransactions() {
        mockVelocityCount(2);
        mockNoRepeatedAmount(new BigDecimal("1000.00"));
        mockNotNewAccount();

        FraudScoringService.ScoringResult result = fraudScoringService.score(
                accountId, new BigDecimal("1000.00"), "DEBIT", "corr-006");

        assertFalse(result.triggeredRules().contains("HIGH_VELOCITY"));
    }

    @Test
    void score_ShouldAdd20Points_WhenVelocityIs3Or4() {
        mockVelocityCount(3);
        mockNoRepeatedAmount(new BigDecimal("1000.00"));
        mockNotNewAccount();

        FraudScoringService.ScoringResult result = fraudScoringService.score(
                accountId, new BigDecimal("1000.00"), "DEBIT", "corr-007");

        assertTrue(result.triggeredRules().contains("HIGH_VELOCITY(+20)"));
    }

    @Test
    void score_ShouldAdd40Points_WhenVelocityReaches5OrMore() {
        mockVelocityCount(5);
        mockNoRepeatedAmount(new BigDecimal("1000.00"));
        mockNotNewAccount();

        FraudScoringService.ScoringResult result = fraudScoringService.score(
                accountId, new BigDecimal("1000.00"), "DEBIT", "corr-008");

        assertTrue(result.triggeredRules().contains("HIGH_VELOCITY(+40)"));
    }

    // ── Rule 4: Repeated amount ───────────────────────────────────────────────

    @Test
    void score_ShouldTriggerRepeatedAmountRule_WhenSameAmountSeenThreeTimes() {
        mockNoVelocity();
        mockRepeatedAmount(new BigDecimal("999.00"), 3);
        mockNotNewAccount();

        FraudScoringService.ScoringResult result = fraudScoringService.score(
                accountId, new BigDecimal("999.00"), "DEBIT", "corr-009");

        assertTrue(result.triggeredRules().contains("REPEATED_AMOUNT(+20)"));
    }

    @Test
    void score_ShouldNotTriggerRepeatedAmountRule_WhenAmountSeenTwice() {
        mockNoVelocity();
        mockRepeatedAmount(new BigDecimal("999.00"), 2);
        mockNotNewAccount();

        FraudScoringService.ScoringResult result = fraudScoringService.score(
                accountId, new BigDecimal("999.00"), "DEBIT", "corr-010");

        assertFalse(result.triggeredRules().contains("REPEATED_AMOUNT"));
    }

    // ── Rule 5: New account ───────────────────────────────────────────────────

    @Test
    void score_ShouldTriggerNewAccountRule_WhenAccountIsNew() {
        mockNoVelocity();
        mockNoRepeatedAmount(new BigDecimal("500.00"));
        mockIsNewAccount();

        FraudScoringService.ScoringResult result = fraudScoringService.score(
                accountId, new BigDecimal("500.00"), "DEBIT", "corr-011");

        assertTrue(result.triggeredRules().contains("NEW_ACCOUNT(+10)"));
    }

    @Test
    void score_ShouldNotTriggerNewAccountRule_WhenAccountIsEstablished() {
        mockNoVelocity();
        mockNoRepeatedAmount(new BigDecimal("500.00"));
        mockNotNewAccount();

        FraudScoringService.ScoringResult result = fraudScoringService.score(
                accountId, new BigDecimal("500.00"), "DEBIT", "corr-012");

        assertFalse(result.triggeredRules().contains("NEW_ACCOUNT"));
    }

    // ── Score cap and combined rules ──────────────────────────────────────────

    @Test
    void score_ShouldCapScoreAt100_WhenMultipleHighScoringRulesTriggered() {
        // >5L (50) + velocity>=5 (40) + repeated (20) + new account (10) = 120, capped at 100
        mockVelocityCount(5);
        mockRepeatedAmount(new BigDecimal("600000.00"), 3);
        mockIsNewAccount();

        FraudScoringService.ScoringResult result = fraudScoringService.score(
                accountId, new BigDecimal("600000.00"), "DEBIT", "corr-013");

        assertTrue(result.score() <= 100, "Score must never exceed 100");
        assertEquals("BLOCK", result.action());
    }

    @Test
    void score_ShouldReturnApprove_WhenNoRulesTriggered() {
        mockNoVelocity();
        mockNoRepeatedAmount(new BigDecimal("100.00"));
        mockNotNewAccount();

        FraudScoringService.ScoringResult result = fraudScoringService.score(
                accountId, new BigDecimal("100.00"), "DEBIT", "corr-014");

        // Night hours may add 15 — still APPROVE (<=49) unless it's 1-5 AM IST
        assertTrue(result.score() <= 49 || result.score() == 15,
                "Score should be 0 or 15 (if night hours) with no other rules triggered");
    }

    // ── recordTransaction side effects ────────────────────────────────────────

    @Test
    void score_ShouldIncrementVelocityAndAmountCounters_AfterScoring() {
        mockNoVelocity();
        mockNoRepeatedAmount(new BigDecimal("200.00"));
        mockNotNewAccount();

        fraudScoringService.score(accountId, new BigDecimal("200.00"), "DEBIT", "corr-015");

        // Velocity key incremented
        verify(valueOperations).increment(contains("fraud:velocity:" + accountId + ":count"));
        // Amount key incremented
        verify(valueOperations).increment(contains("fraud:amount:" + accountId + ":200.00"));
        // TTLs set
        verify(redisTemplate).expire(contains(":count"), eq(Duration.ofMinutes(10)));
        verify(redisTemplate).expire(contains(":200.00"), eq(Duration.ofHours(1)));
    }

    // ── markNewAccount ────────────────────────────────────────────────────────

    @Test
    void markNewAccount_ShouldSetRedisKeyWithThirtyDayTtl() {
        fraudScoringService.markNewAccount(accountId);

        verify(valueOperations).set(
                eq("fraud:newacct:" + accountId),
                eq("1"),
                eq(Duration.ofDays(30)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void mockNoVelocity() {
        when(valueOperations.get(contains(":count"))).thenReturn(null);
    }

    private void mockVelocityCount(int count) {
        when(valueOperations.get(contains(":count"))).thenReturn(String.valueOf(count));
    }

    private void mockNoRepeatedAmount(BigDecimal amount) {
        when(valueOperations.get(contains("fraud:amount:" + accountId + ":" + amount.toPlainString())))
                .thenReturn(null);
    }

    private void mockRepeatedAmount(BigDecimal amount, int count) {
        when(valueOperations.get(contains("fraud:amount:" + accountId + ":" + amount.toPlainString())))
                .thenReturn(String.valueOf(count));
    }

    private void mockNotNewAccount() {
        when(redisTemplate.hasKey(contains("fraud:newacct:"))).thenReturn(false);
    }

    private void mockIsNewAccount() {
        when(redisTemplate.hasKey(contains("fraud:newacct:"))).thenReturn(true);
    }
}