package com.rewabank.accounts.services;

import com.rewabank.accounts.dto.BalanceResponse;
import com.rewabank.accounts.entity.Account;
import com.rewabank.accounts.repository.AccountsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real Redis integration test for CQRS read-side caching.
 *
 * Why this test exists:
 *   - All previous cache tests mock RedisTemplate, so they never prove that
 *     Spring's @Cacheable / @CachePut / @CacheEvict annotations bind correctly
 *     to the actual Redis data structure.
 *   - This test runs a real Valkey/Redis container and exercises the full
 *     cache-through, cache-hit, and cache-eviction paths.
 *
 * What is tested:
 *   1. Cache miss — first getBalance() call fetches from DB, caches in Redis
 *   2. Cache hit  — second getBalance() call returns Redis value (no DB query)
 *   3. Cache eviction — evictBalanceCache() removes the key
 *   4. Cache update — updateBalanceCache() refreshes the cached balance
 *   5. Stale read prevention — evict on freeze ensures next read hits DB
 */
@SpringBootTest
@Testcontainers
class AccountReadServiceRedisIT {

    @SuppressWarnings("resource")
    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.data.redis.host",     redis::getHost);
        r.add("spring.data.redis.port",     () -> redis.getMappedPort(6379).toString());
        r.add("spring.data.redis.password", () -> "");
        r.add("spring.cache.type",          () -> "redis");
        // Use H2 for the read-side DB — this test is about Redis caching, not DB locking
        r.add("spring.datasource.url",                  () -> "jdbc:h2:mem:redistestdb;MODE=PostgreSQL");
        r.add("spring.datasource.driver-class-name",    () -> "org.h2.Driver");
        r.add("spring.datasource.username",             () -> "sa");
        r.add("spring.datasource.password",             () -> "");
        r.add("spring.jpa.database-platform",           () -> "org.hibernate.dialect.H2Dialect");
        r.add("spring.jpa.hibernate.ddl-auto",          () -> "create-drop");
        r.add("spring.flyway.enabled",                  () -> "false");
        r.add("spring.autoconfigure.exclude", () ->
                "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration");
        r.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://localhost:8080/auth/realms/test");
        r.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:8080/auth/realms/test/protocol/openid-connect/certs");
    }

    @Autowired AccountReadService  accountReadService;
    @Autowired AccountsRepository  accountsRepository;

    private Account account;
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("3000.00");
    private static final BigDecimal MIN_BALANCE     = new BigDecimal("500.00");

    @BeforeEach
    void setUp() {
        accountsRepository.deleteAll();
        account = accountsRepository.saveAndFlush(
                Account.builder()
                        .accountNumber("REDIS-ACCT-001")
                        .keycloakUserId("redis-test-user")
                        .customerId(UUID.randomUUID())
                        .accountType(Account.AccountType.SAVINGS)
                        .status(Account.AccountStatus.ACTIVE)
                        .balance(INITIAL_BALANCE)
                        .minimumBalance(MIN_BALANCE)
                        .currency("INR")
                        .branchCode("BR001")
                        .ifscCode("RWAB001")
                        .build());

        // Ensure cache is clean before each test
        accountReadService.evictBalanceCache(account.getAccountNumber());
    }

    // ── Test 1: Cache miss on first call, cache hit on second ─────────────────

    @Test
    void getBalance_FirstCall_FetchesFromDb_SecondCall_HitsCache() {
        // First call — cache miss, fetches from DB
        BalanceResponse first = accountReadService.getBalance(account.getAccountNumber());
        assertNotNull(first);
        assertEquals(account.getAccountNumber(), first.accountNumber());
        assertEquals(0, INITIAL_BALANCE.compareTo(first.balance()));

        // Mutate DB directly (bypassing service/cache) to prove next call hits Redis
        account.setBalance(new BigDecimal("9999.00"));
        accountsRepository.saveAndFlush(account);

        // Second call — must still return original cached value (3000), not mutated DB value
        BalanceResponse cached = accountReadService.getBalance(account.getAccountNumber());
        assertEquals(0, INITIAL_BALANCE.compareTo(cached.balance()),
                "Second call must serve Redis cached value, not the mutated DB value");
    }

    // ── Test 2: @CacheEvict clears the key — next read returns fresh DB value ─

    @Test
    void evictBalanceCache_ThenGetBalance_ReturnsFreshDbValue() {
        // Populate cache
        accountReadService.getBalance(account.getAccountNumber());

        // Mutate DB and evict cache
        account.setBalance(new BigDecimal("1500.00"));
        accountsRepository.saveAndFlush(account);
        accountReadService.evictBalanceCache(account.getAccountNumber());

        // Next read must go to DB and return the mutated value
        BalanceResponse fresh = accountReadService.getBalance(account.getAccountNumber());
        assertEquals(0, new BigDecimal("1500.00").compareTo(fresh.balance()),
                "After eviction, getBalance must return the updated DB value, not stale cache");
    }

    // ── Test 3: @CachePut updates cached value without evicting ──────────────

    @Test
    void updateBalanceCache_UpdatesCachedValue_SubsequentReadReturnsUpdated() {
        // Populate cache with initial value
        accountReadService.getBalance(account.getAccountNumber());

        // Update balance and push to cache via @CachePut
        account.setBalance(new BigDecimal("7500.00"));
        accountsRepository.saveAndFlush(account);
        accountReadService.updateBalanceCache(account);

        // DB is now out-of-sync with Redis — we mutate DB back to verify cache wins
        account.setBalance(new BigDecimal("100.00"));
        accountsRepository.saveAndFlush(account);

        // Read from cache — must return the @CachePut value (7500), not DB (100)
        BalanceResponse fromCache = accountReadService.getBalance(account.getAccountNumber());
        assertEquals(0, new BigDecimal("7500.00").compareTo(fromCache.balance()),
                "@CachePut value must be returned, not the stale DB value");
    }

    // ── Test 4: Available balance = balance - minimumBalance ─────────────────

    @Test
    void getBalance_ReturnsCorrectAvailableBalance() {
        BalanceResponse response = accountReadService.getBalance(account.getAccountNumber());

        BigDecimal expectedAvailable = INITIAL_BALANCE.subtract(MIN_BALANCE); // 2500.00
        assertEquals(0, expectedAvailable.compareTo(response.availableBalance()),
                "availableBalance must equal balance minus minimumBalance");
    }

    // ── Test 5: Frozen account — cache is evicted, subsequent read hits DB ────

    @Test
    void cacheEviction_OnFreezeStatus_PreventsStaleActiveBalanceBeingServed() {
        // Cache the ACTIVE balance
        accountReadService.getBalance(account.getAccountNumber());

        // Freeze account and evict cache (as AccountService.freezeAccount does)
        account.setStatus(Account.AccountStatus.FROZEN);
        accountsRepository.saveAndFlush(account);
        accountReadService.evictBalanceCache(account.getAccountNumber());

        // Next getBalance must return the frozen status from DB, not stale ACTIVE cache
        BalanceResponse afterFreeze = accountReadService.getBalance(account.getAccountNumber());
        assertEquals("FROZEN", afterFreeze.status(),
                "After cache eviction, getBalance must reflect the actual FROZEN status from DB");
    }

    // ── Test 6: Non-existent account number throws, does not cache null ───────

    @Test
    void getBalance_ThrowsAccountException_WhenAccountDoesNotExist() {
        assertThrows(com.rewabank.accounts.exception.AccountException.class,
                () -> accountReadService.getBalance("NONEXISTENT-999"));
    }
}
