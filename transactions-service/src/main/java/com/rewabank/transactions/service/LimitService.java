package com.rewabank.transactions.service;

import com.rewabank.transactions.entity.DailyLimit;
import com.rewabank.transactions.exception.TransactionException;
import com.rewabank.transactions.repository.DailyLimitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LimitService {

    private final DailyLimitRepository dailyLimitRepository;

    // Per-transaction limits by role
    private static final BigDecimal CUSTOMER_MAX_PER_TXN      =
            new BigDecimal("100000.00");   // 1L
    private static final BigDecimal TELLER_MAX_PER_TXN        =
            new BigDecimal("100000.00");   // 1L
    private static final BigDecimal RM_MAX_PER_TXN            =
            new BigDecimal("500000.00");   // 5L
    private static final BigDecimal BRANCH_MANAGER_MAX_PER_TXN =
            new BigDecimal("1000000.00");  // 10L

    @Transactional
    public void validateAndConsumeLimit(String keycloakUserId,
                                        BigDecimal amount,
                                        String userRole) {
        // Per-transaction limit check
        BigDecimal maxPerTxn = perTransactionLimit(userRole);
        if (amount.compareTo(maxPerTxn) > 0) {
            throw new TransactionException("TXN_002",
                    "Amount ₹" + amount + " exceeds per-transaction limit of ₹"
                            + maxPerTxn + " for role " + userRole);
        }

        // Daily limit check + consume (row is locked for the duration of this transaction)
        DailyLimit limit = getOrCreateDailyLimit(keycloakUserId);
        if (!limit.hasCapacity(amount)) {
            throw new TransactionException("TXN_003",
                    "Daily limit exceeded. Remaining: ₹" + limit.remainingLimit());
        }

        limit.setUsedAmount(limit.getUsedAmount().add(amount));
        dailyLimitRepository.save(limit);
        log.debug("Daily limit consumed: ₹{} remaining: ₹{}",
                amount, limit.remainingLimit());
    }

    private DailyLimit getOrCreateDailyLimit(String keycloakUserId) {
        // SELECT FOR UPDATE — holds the row lock until the outer @Transactional commits,
        // preventing concurrent requests from both passing the capacity check.
        Optional<DailyLimit> existing = dailyLimitRepository
                .findWithLockByKeycloakUserIdAndLimitDate(keycloakUserId, LocalDate.now());
        if (existing.isPresent()) {
            return existing.get();
        }

        // Row doesn't exist yet — first transaction of the day for this user.
        // saveAndFlush forces the INSERT immediately so a concurrent thread's
        // UNIQUE(keycloak_user_id, limit_date) violation surfaces here, not at commit.
        try {
            return dailyLimitRepository.saveAndFlush(
                    DailyLimit.builder()
                            .keycloakUserId(keycloakUserId)
                            .limitDate(LocalDate.now())
                            .build());
        } catch (DataIntegrityViolationException e) {
            // Another concurrent thread inserted first — lock and return their row.
            return dailyLimitRepository
                    .findWithLockByKeycloakUserIdAndLimitDate(keycloakUserId, LocalDate.now())
                    .orElseThrow(() -> new TransactionException("TXN_009",
                            "Failed to acquire daily limit record"));
        }
    }

    private BigDecimal perTransactionLimit(String role) {
        if (role == null) return CUSTOMER_MAX_PER_TXN;
        return switch (role.toUpperCase()) {
            case "BRANCH_MANAGER", "SUPER_ADMIN" -> BRANCH_MANAGER_MAX_PER_TXN;
            case "RELATIONSHIP_MANAGER"           -> RM_MAX_PER_TXN;
            case "TELLER"                         -> TELLER_MAX_PER_TXN;
            default                               -> CUSTOMER_MAX_PER_TXN;
        };
    }
}
