package com.rewabank.accounts.services;

import com.rewabank.accounts.client.CustomersFeignClient;
import com.rewabank.accounts.entity.Account;
import com.rewabank.accounts.exception.AccountException;
import com.rewabank.accounts.repository.AccountsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real PostgreSQL integration test for pessimistic-lock correctness.
 *
 * Why this test exists:
 *   - H2 silently ignores "FOR NO KEY UPDATE" (Hibernate 6 translates
 *     PESSIMISTIC_WRITE to that on PostgreSQL), so H2-based tests can never
 *     prove that concurrent debits are serialized correctly.
 *   - This test fires N concurrent debit threads against a real PostgreSQL
 *     container and asserts: balance never drops below minimum, the correct
 *     number of transactions succeed, and the DB is left in a consistent state.
 *
 * What is tested:
 *   1. @Lock PESSIMISTIC_WRITE serializes concurrent balance mutations
 *   2. Balance floor (minimumBalance) is enforced under real concurrency
 *   3. No phantom reads — each debit thread sees the committed post-debit balance
 *   4. Failed debits throw AccountException(ACCT_007), not a data-corruption exception
 */
@SpringBootTest
@Testcontainers
class AccountPessimisticLockPostgresIT {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.database-platform",       () -> "org.hibernate.dialect.PostgreSQLDialect");
        r.add("spring.jpa.hibernate.ddl-auto",      () -> "create-drop");
        r.add("spring.flyway.enabled",              () -> "false");
        // Disable caching entirely — this test is about DB locking, not Redis caching
        r.add("spring.cache.type",                  () -> "none");
        r.add("spring.autoconfigure.exclude", () ->
                "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration");
        r.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://localhost:8080/auth/realms/test");
        r.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:8080/auth/realms/test/protocol/openid-connect/certs");
    }

    @Autowired AccountService       accountService;
    @Autowired AccountsRepository   accountsRepository;

    @SuppressWarnings("deprecation") @MockBean CustomersFeignClient       customersFeignClient;
    @SuppressWarnings("deprecation") @MockBean RedisTemplate<String, Object> redisTemplate;

    private Account account;

    private static final BigDecimal INITIAL_BALANCE  = new BigDecimal("5000.00");
    private static final BigDecimal MIN_BALANCE       = new BigDecimal("1000.00");
    private static final BigDecimal DEBIT_AMOUNT      = new BigDecimal("1000.00");
    // available = 5000 - 1000 = 4000 → 4 debits of 1000 succeed, 5th+ fail
    private static final int EXPECTED_SUCCESSES       = 4;

    @BeforeEach
    void setUp() {
        accountsRepository.deleteAll();
        account = accountsRepository.saveAndFlush(
                Account.builder()
                        .accountNumber("PG" + UUID.randomUUID().toString().replace("-", "").substring(0, 10))
                        .keycloakUserId("pg-test-user")
                        .customerId(UUID.randomUUID())
                        .accountType(Account.AccountType.SAVINGS)
                        .status(Account.AccountStatus.ACTIVE)
                        .balance(INITIAL_BALANCE)
                        .minimumBalance(MIN_BALANCE)
                        .currency("INR")
                        .branchCode("BR001")
                        .ifscCode("RWAB001")
                        .build());
    }

    // ── Test 1: Sequential pessimistic lock — balance floor enforced ──────────

    @Test
    void sequentialDebits_BalanceNeverDropsBelowMinimum_WithRealPostgres() {
        int successes = 0;
        for (int i = 0; i < 6; i++) {
            try {
                accountService.debit(account.getId(), DEBIT_AMOUNT, "txn-seq-" + i);
                successes++;
            } catch (AccountException e) {
                assertEquals("ACCT_007", e.getErrorCode(),
                        "Should fail with insufficient balance, not another error");
            }
        }

        assertEquals(EXPECTED_SUCCESSES, successes,
                "Exactly 4 debits of 1000 should succeed (available = 4000)");

        Account fresh = accountsRepository.findById(account.getId()).orElseThrow();
        assertEquals(0, MIN_BALANCE.compareTo(fresh.getBalance()),
                "Balance must equal minimum balance after draining all available funds");
    }

    // ── Test 2: Concurrent pessimistic lock — no double-spend ─────────────────

    @Test
    void concurrentDebits_PessimisticLockPreventsDoubleSpend() throws InterruptedException {
        int threads = 8;
        ExecutorService pool   = Executors.newFixedThreadPool(threads);
        AtomicInteger   wins   = new AtomicInteger(0);
        AtomicInteger   losses = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final String txnId = "concurrent-" + i;
            futures.add(pool.submit(() -> {
                try {
                    accountService.debit(account.getId(), DEBIT_AMOUNT, txnId);
                    wins.incrementAndGet();
                } catch (AccountException e) {
                    if ("ACCT_007".equals(e.getErrorCode())) {
                        losses.incrementAndGet();
                    } else {
                        throw new RuntimeException("Unexpected error code: " + e.getErrorCode(), e);
                    }
                }
            }));
        }

        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        // Verify all futures completed without unexpected exceptions
        for (Future<?> f : futures) {
            assertDoesNotThrow(() -> f.get(),
                    "Thread should complete without unexpected exception");
        }

        // Critical invariant: wins + losses = total threads
        assertEquals(threads, wins.get() + losses.get(),
                "Every thread must either succeed or get ACCT_007 — no other outcome");

        // Critical invariant: exactly EXPECTED_SUCCESSES should win
        assertEquals(EXPECTED_SUCCESSES, wins.get(),
                "Exactly 4 concurrent debits should win (no double-spend)");

        // Critical invariant: final balance must be exactly minimum balance
        Account fresh = accountsRepository.findById(account.getId()).orElseThrow();
        assertEquals(0, MIN_BALANCE.compareTo(fresh.getBalance()),
                "Pessimistic lock must leave balance at exactly minimum — no phantom reads allowed");
    }

    // ── Test 3: Concurrent mixed debit/credit — balance stays consistent ──────

    @Test
    void concurrentMixedOps_BalanceRemainsConsistent() throws InterruptedException {
        int debitThreads  = 3;
        int creditThreads = 3;
        int total         = debitThreads + creditThreads;

        ExecutorService pool    = Executors.newFixedThreadPool(total);
        AtomicInteger   debits  = new AtomicInteger(0);
        AtomicInteger   credits = new AtomicInteger(0);
        CyclicBarrier   barrier = new CyclicBarrier(total);

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < debitThreads; i++) {
            final String txnId = "mixed-debit-" + i;
            futures.add(pool.submit(() -> {
                try { barrier.await(); } catch (Exception ignored) {}
                try {
                    accountService.debit(account.getId(), new BigDecimal("500.00"), txnId);
                    debits.incrementAndGet();
                } catch (AccountException ignored) {}
            }));
        }

        for (int i = 0; i < creditThreads; i++) {
            final String txnId = "mixed-credit-" + i;
            futures.add(pool.submit(() -> {
                try { barrier.await(); } catch (Exception ignored) {}
                accountService.credit(account.getId(), new BigDecimal("500.00"), txnId);
                credits.incrementAndGet();
            }));
        }

        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        Account fresh = accountsRepository.findById(account.getId()).orElseThrow();
        BigDecimal expected = INITIAL_BALANCE
                .subtract(new BigDecimal("500.00").multiply(BigDecimal.valueOf(debits.get())))
                .add(new BigDecimal("500.00").multiply(BigDecimal.valueOf(credits.get())));

        assertEquals(0, expected.compareTo(fresh.getBalance()),
                "Balance must reflect exactly the committed debits and credits — " +
                        debits.get() + " debits, " + credits.get() + " credits");

        assertTrue(fresh.getBalance().compareTo(MIN_BALANCE) >= 0,
                "Balance must never drop below minimum balance even under concurrent load");
    }

    // ── Test 4: Frozen account rejects debits under real DB ──────────────────

    @Test
    void debit_ThrowsAcCT006_WhenAccountIsFrozen_WithRealPostgres() {
        account.setStatus(Account.AccountStatus.FROZEN);
        accountsRepository.saveAndFlush(account);

        AccountException ex = assertThrows(AccountException.class,
                () -> accountService.debit(account.getId(), DEBIT_AMOUNT, "frozen-txn"));

        assertEquals("ACCT_006", ex.getErrorCode());

        Account fresh = accountsRepository.findById(account.getId()).orElseThrow();
        assertEquals(0, INITIAL_BALANCE.compareTo(fresh.getBalance()),
                "Balance must be unchanged when debit is rejected due to frozen status");
    }

    // ── Test 5: Lock timeout — second transaction waits, then proceeds ────────
    //    Two sequential transactions on the same account must serialize correctly.

    @Test
    void twoSequentialTransactions_BothCommit_WithRealPostgres() {
        accountService.debit(account.getId(),  new BigDecimal("500.00"),  "seq-d1");
        accountService.credit(account.getId(), new BigDecimal("1000.00"), "seq-c1");
        accountService.debit(account.getId(),  new BigDecimal("500.00"),  "seq-d2");

        Account fresh = accountsRepository.findById(account.getId()).orElseThrow();
        // 5000 - 500 + 1000 - 500 = 5000
        assertEquals(0, new BigDecimal("5000.00").compareTo(fresh.getBalance()),
                "5000 - 500 + 1000 - 500 = 5000");
    }
}
