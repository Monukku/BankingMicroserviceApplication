package com.rewabank.accounts.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.accounts.client.CustomersFeignClient;
import com.rewabank.accounts.entity.Account;
import com.rewabank.accounts.exception.AccountException;
import com.rewabank.accounts.repository.AccountsRepository;
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
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests debit/credit business-logic correctness: balance floor enforcement,
 * invalid-amount rejection, and sequential accumulation.
 *
 * NOTE: DB-level pessimistic lock (@Lock PESSIMISTIC_WRITE) correctness cannot
 * be verified with H2 — Hibernate 6 generates "FOR NO KEY UPDATE" which H2 does
 * not understand. A full concurrency regression test requires Testcontainers +
 * PostgreSQL. These tests cover the business rules independently of locking.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountConcurrencyTest {

    @Mock private AccountsRepository          accountsRepository;
    @Mock private OutboxEventSaver            outboxEventSaver;
    @Mock private CustomersFeignClient        customersFeignClient;
    @Mock private AccountReadService          accountReadService;
    @Mock private ObjectMapper                objectMapper;
    @Mock private RedisTemplate<String, Object> redisTemplate;

    @Spy @InjectMocks private AccountService accountService;

    private Account account;

    @BeforeEach
    void setUp() throws Exception {
        account = Account.builder()
                .id(UUID.randomUUID())
                .accountNumber("199999999999")
                .keycloakUserId("kc-user-test")
                .customerId(UUID.randomUUID())
                .accountType(Account.AccountType.SAVINGS)
                .status(Account.AccountStatus.ACTIVE)
                .balance(new BigDecimal("5000.00"))
                .minimumBalance(new BigDecimal("1000.00"))
                .currency("INR")
                .build();

        // findByIdForUpdate returns the same account object so balance changes accumulate
        when(accountsRepository.findByIdForUpdate(account.getId()))
                .thenReturn(Optional.of(account));
        when(accountsRepository.save(any(Account.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ── debit: balance floor enforcement ─────────────────────────────────────

    @Test
    void sequentialDebits_ExactlyFourShouldSucceed_FifthMustFail() {
        // available = 5000 - 1000(min) = 4000 → 4 × 1000 succeeds, 5th fails
        for (int i = 0; i < 4; i++) {
            final int attempt = i;
            assertDoesNotThrow(
                    () -> accountService.debit(account.getId(),
                            new BigDecimal("1000.00"), "txn-" + attempt),
                    "Debit " + (i + 1) + " should succeed");
        }

        AccountException ex = assertThrows(AccountException.class,
                () -> accountService.debit(account.getId(),
                        new BigDecimal("1000.00"), "txn-5"));
        assertEquals("ACCT_007", ex.getErrorCode(),
                "5th debit must fail: insufficient balance");
    }

    @Test
    void sequentialDebits_BalanceMustEqualMinimumAfterDrainingAvailable() {
        // Drain all 4000 available
        for (int i = 0; i < 4; i++) {
            accountService.debit(account.getId(),
                    new BigDecimal("1000.00"), "drain-" + i);
        }

        assertEquals(0,
                new BigDecimal("1000.00").compareTo(account.getBalance()),
                "Balance must equal minimum balance after draining all available funds");
    }

    @Test
    void debit_ShouldFail_WhenSingleAmountExceedsAvailableBalance() {
        // available = 4000, debit 4001
        AccountException ex = assertThrows(AccountException.class,
                () -> accountService.debit(account.getId(),
                        new BigDecimal("4001.00"), "txn-big"));
        assertEquals("ACCT_007", ex.getErrorCode());
    }

    @Test
    void debit_ShouldFail_WhenAmountIsZero() {
        AccountException ex = assertThrows(AccountException.class,
                () -> accountService.debit(account.getId(),
                        BigDecimal.ZERO, "txn-zero"));
        assertEquals("ACCT_005", ex.getErrorCode());
    }

    @Test
    void debit_ShouldFail_WhenAmountIsNegative() {
        AccountException ex = assertThrows(AccountException.class,
                () -> accountService.debit(account.getId(),
                        new BigDecimal("-100.00"), "txn-neg"));
        assertEquals("ACCT_005", ex.getErrorCode());
    }

    @Test
    void debit_ShouldFail_OnFrozenAccount() {
        account.setStatus(Account.AccountStatus.FROZEN);

        AccountException ex = assertThrows(AccountException.class,
                () -> accountService.debit(account.getId(),
                        new BigDecimal("500.00"), "txn-frozen"));
        assertEquals("ACCT_006", ex.getErrorCode());
    }

    // ── credit: accumulation ──────────────────────────────────────────────────

    @Test
    void sequentialCredits_ShouldAccumulateCorrectly() {
        for (int i = 0; i < 5; i++) {
            accountService.credit(account.getId(),
                    new BigDecimal("1000.00"), "credit-" + i);
        }

        assertEquals(0,
                new BigDecimal("10000.00").compareTo(account.getBalance()),
                "5000 initial + 5 × 1000 credits = 10000");
    }

    @Test
    void credit_ShouldFail_WhenAmountIsZero() {
        AccountException ex = assertThrows(AccountException.class,
                () -> accountService.credit(account.getId(),
                        BigDecimal.ZERO, "credit-zero"));
        assertEquals("ACCT_005", ex.getErrorCode());
    }

    @Test
    void interleavedDebitAndCredit_BalanceMustRemainConsistent() {
        // Debit 2000, credit 3000, debit 1000 → final = 5000 - 2000 + 3000 - 1000 = 5000
        accountService.debit(account.getId(),  new BigDecimal("2000.00"), "d1");
        accountService.credit(account.getId(), new BigDecimal("3000.00"), "c1");
        accountService.debit(account.getId(),  new BigDecimal("1000.00"), "d2");

        assertEquals(0,
                new BigDecimal("5000.00").compareTo(account.getBalance()),
                "5000 - 2000 + 3000 - 1000 = 5000");
    }
}
