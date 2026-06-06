package com.rewabank.accounts.services;

import com.rewabank.accounts.client.CustomersFeignClient;
import com.rewabank.accounts.dto.BalanceResponse;
import com.rewabank.accounts.entity.Account;
import com.rewabank.accounts.exception.AccountException;
import com.rewabank.accounts.repository.AccountsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies @Cacheable / @CachePut / @CacheEvict behaviour on AccountReadService.
 * Uses an explicit ConcurrentMapCacheManager (in-memory) so Spring's caching proxy
 * is active without needing Redis, regardless of spring.cache.type=none in the test profile.
 */
@SpringBootTest
@ActiveProfiles("test")
class AccountReadServiceCacheTest {

    @TestConfiguration
    @EnableCaching
    static class CacheTestConfig {
        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("account-balance");
        }
    }

    @Autowired private AccountReadService accountReadService;
    @Autowired private CacheManager       cacheManager;

    @SuppressWarnings("deprecation") @MockBean private AccountsRepository      accountsRepository;
    @SuppressWarnings("deprecation") @MockBean private CustomersFeignClient    customersFeignClient;
    @SuppressWarnings("deprecation") @MockBean private RedisTemplate<String, Object> redisTemplate;

    private static final String ACCOUNT_NUMBER = "123456789012";

    private Account account;

    @BeforeEach
    void setUp() {
        cacheManager.getCache("account-balance").clear();

        account = Account.builder()
                .accountNumber(ACCOUNT_NUMBER)
                .balance(new BigDecimal("5000.00"))
                .minimumBalance(new BigDecimal("1000.00"))
                .currency("INR")
                .status(Account.AccountStatus.ACTIVE)
                .build();

        when(accountsRepository.findByAccountNumberAndDeletedAtIsNull(ACCOUNT_NUMBER))
                .thenReturn(Optional.of(account));
    }

    // ── @Cacheable — cache miss and hit ──────────────────────────────────────

    @Test
    void getBalance_ShouldHitDatabase_OnCacheMiss() {
        accountReadService.getBalance(ACCOUNT_NUMBER);
        verify(accountsRepository, times(1))
                .findByAccountNumberAndDeletedAtIsNull(ACCOUNT_NUMBER);
    }

    @Test
    void getBalance_ShouldNotHitDatabase_OnCacheHit() {
        accountReadService.getBalance(ACCOUNT_NUMBER); // primes cache
        accountReadService.getBalance(ACCOUNT_NUMBER); // cache hit
        accountReadService.getBalance(ACCOUNT_NUMBER); // cache hit

        // Repository called only once despite three getBalance calls
        verify(accountsRepository, times(1))
                .findByAccountNumberAndDeletedAtIsNull(ACCOUNT_NUMBER);
    }

    @Test
    void getBalance_ShouldReturnCorrectBalance_FromCache() {
        BalanceResponse first  = accountReadService.getBalance(ACCOUNT_NUMBER);
        BalanceResponse second = accountReadService.getBalance(ACCOUNT_NUMBER);

        assertEquals(first.balance(), second.balance());
        assertEquals(new BigDecimal("5000.00"), second.balance());
    }

    // ── @CacheEvict — forces DB lookup after eviction ─────────────────────────

    @Test
    void evictBalanceCache_ShouldForceDbLookup_OnNextGet() {
        accountReadService.getBalance(ACCOUNT_NUMBER); // prime cache
        accountReadService.evictBalanceCache(ACCOUNT_NUMBER); // evict
        accountReadService.getBalance(ACCOUNT_NUMBER); // must hit DB again

        // DB should have been called twice: once on cache miss, once after eviction
        verify(accountsRepository, times(2))
                .findByAccountNumberAndDeletedAtIsNull(ACCOUNT_NUMBER);
    }

    @Test
    void evictBalanceCache_ShouldRemoveEntryFromCache() {
        accountReadService.getBalance(ACCOUNT_NUMBER);
        accountReadService.evictBalanceCache(ACCOUNT_NUMBER);

        assertNull(cacheManager.getCache("account-balance").get(ACCOUNT_NUMBER),
                "Cache entry must be null after eviction");
    }

    // ── @CachePut — updates cache without reading from DB ────────────────────

    @Test
    void updateBalanceCache_ShouldUpdateCachedBalance_WithoutDbRead() {
        accountReadService.getBalance(ACCOUNT_NUMBER); // prime with 5000

        // Simulate balance change after a transaction
        account.setBalance(new BigDecimal("3000.00"));
        accountReadService.updateBalanceCache(account); // update cache directly

        // Next read must return updated value WITHOUT hitting DB
        reset(accountsRepository); // clear call count
        BalanceResponse response = accountReadService.getBalance(ACCOUNT_NUMBER);

        assertEquals(0, new BigDecimal("3000.00").compareTo(response.balance()),
                "getBalance must return the updated cache value");
        verifyNoInteractions(accountsRepository); // DB must not be called
    }

    @Test
    void updateBalanceCache_ShouldNotRequireDbCall() {
        account.setBalance(new BigDecimal("4500.00"));
        accountReadService.updateBalanceCache(account);

        // updateBalanceCache is a pure cache write — no DB access
        verifyNoInteractions(accountsRepository);
    }

    // ── exception on cache miss with non-existent account ────────────────────

    @Test
    void getBalance_ShouldThrow_WhenAccountNotFoundAndCacheMiss() {
        when(accountsRepository.findByAccountNumberAndDeletedAtIsNull("unknown"))
                .thenReturn(Optional.empty());

        AccountException ex = assertThrows(AccountException.class,
                () -> accountReadService.getBalance("unknown"));
        assertEquals("ACCT_002", ex.getErrorCode());
    }
}
