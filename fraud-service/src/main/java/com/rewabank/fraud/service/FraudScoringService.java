package com.rewabank.fraud.service;

import com.rewabank.fraud.model.FraudRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Redis rule engine — must respond in <100ms.
 * All rules evaluated against Redis data only.
 * No DB calls on the scoring hot path.
 *
 * Rules applied:
 * 1. Large amount rule     — amount > threshold = +30 score
 * 2. Velocity rule         — too many txns in window = +40 score
 * 3. Night hours rule      — transaction between 1-5 AM = +15 score
 * 4. Repeated amount rule  — same amount 3+ times = +20 score
 * 5. New account rule      — account < 30 days old = +10 score
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FraudScoringService {

    private final RedisTemplate<String, Object> redisTemplate;

    // Redis key prefixes
    private static final String VELOCITY_KEY   = "fraud:velocity:";
    private static final String AMOUNT_KEY     = "fraud:amount:";
    private static final String NEW_ACCT_KEY   = "fraud:newacct:";

    // Rule thresholds
    private static final BigDecimal LARGE_AMOUNT_THRESHOLD =
            new BigDecimal("50000.00");    // ₹50,000
    private static final int VELOCITY_LIMIT = 5; // max 5 txns per 10 min

    /**
     * Score a transaction — returns 0-100.
     * Called synchronously by Transactions MS.
     * Must complete in <100ms.
     */
    public ScoringResult score(UUID accountId, BigDecimal amount,
                               String transactionType, String correlationId) {
        long startMs = System.currentTimeMillis();
        List<String> triggeredRules = new ArrayList<>();
        int totalScore = 0;

        // Rule 1: Large amount
        if (amount.compareTo(LARGE_AMOUNT_THRESHOLD) > 0) {
            int contribution = calculateLargeAmountScore(amount);
            totalScore += contribution;
            triggeredRules.add("LARGE_AMOUNT(+" + contribution + ")");
        }

        // Rule 2: Velocity check — too many transactions in 10 min
        int velocityScore = checkVelocity(accountId);
        if (velocityScore > 0) {
            totalScore += velocityScore;
            triggeredRules.add("HIGH_VELOCITY(+" + velocityScore + ")");
        }

        // Rule 3: Night hours (1 AM - 5 AM IST)
        int hour = java.time.LocalTime.now(
                java.time.ZoneId.of("Asia/Kolkata")).getHour();
        if (hour >= 1 && hour <= 5) {
            totalScore += 15;
            triggeredRules.add("NIGHT_HOURS(+15)");
        }

        // Rule 4: Repeated amount pattern
        if (isRepeatedAmount(accountId, amount)) {
            totalScore += 20;
            triggeredRules.add("REPEATED_AMOUNT(+20)");
        }

        // Rule 5: New account (registered in Redis < 30 days)
        if (isNewAccount(accountId)) {
            totalScore += 10;
            triggeredRules.add("NEW_ACCOUNT(+10)");
        }

        // Cap at 100
        totalScore = Math.min(totalScore, 100);
        String action = FraudRule.actionFor(totalScore);

        // Record this transaction in velocity window
        recordTransaction(accountId, amount);

        long elapsed = System.currentTimeMillis() - startMs;
        log.debug("Fraud score for account: {} score: {} action: {} rules: {} elapsed: {}ms",
                accountId, totalScore, action, triggeredRules, elapsed);

        if (elapsed > 100) {
            log.warn("Fraud scoring exceeded 100ms: {}ms for account: {}",
                    elapsed, accountId);
        }

        return new ScoringResult(totalScore, action,
                String.join(", ", triggeredRules));
    }

    private int calculateLargeAmountScore(BigDecimal amount) {
        // Graduated score based on amount
        if (amount.compareTo(new BigDecimal("500000")) > 0) return 50; // >5L
        if (amount.compareTo(new BigDecimal("200000")) > 0) return 40; // >2L
        if (amount.compareTo(new BigDecimal("100000")) > 0) return 35; // >1L
        return 30; // >50K
    }

    private int checkVelocity(UUID accountId) {
        String key = VELOCITY_KEY + accountId + ":count";
        Object count = redisTemplate.opsForValue().get(key);
        int txnCount = count != null
                ? Integer.parseInt(count.toString()) : 0;
        if (txnCount >= VELOCITY_LIMIT) return 40;
        if (txnCount >= 3)              return 20;
        return 0;
    }

    private boolean isRepeatedAmount(UUID accountId, BigDecimal amount) {
        String key = AMOUNT_KEY + accountId + ":" + amount.toPlainString();
        Object count = redisTemplate.opsForValue().get(key);
        return count != null && Integer.parseInt(count.toString()) >= 3;
    }

    private boolean isNewAccount(UUID accountId) {
        String key = NEW_ACCT_KEY + accountId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    private void recordTransaction(UUID accountId, BigDecimal amount) {
        // Increment velocity counter (10 min window)
        String velocityKey = VELOCITY_KEY + accountId + ":count";
        redisTemplate.opsForValue().increment(velocityKey);
        redisTemplate.expire(velocityKey, Duration.ofMinutes(10));

        // Track amount repetition (1 hour window)
        String amountKey = AMOUNT_KEY + accountId + ":"
                + amount.toPlainString();
        redisTemplate.opsForValue().increment(amountKey);
        redisTemplate.expire(amountKey, Duration.ofHours(1));
    }

    // Mark account as new (called when account created)
    public void markNewAccount(UUID accountId) {
        String key = NEW_ACCT_KEY + accountId;
        redisTemplate.opsForValue().set(key, "1",
                Duration.ofDays(30));
        log.debug("Account marked as new in fraud engine: {}", accountId);
    }

    public record ScoringResult(int score, String action, String triggeredRules) {}
}
