package com.rewabank.accounts.services;

import com.rewabank.accounts.dto.BalanceResponse;
import com.rewabank.accounts.entity.Account;
import com.rewabank.accounts.exception.AccountException;
import com.rewabank.accounts.repository.AccountsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * CQRS read side.
 * Balance reads served from Redis (5 min TTL).
 * Cache updated on every write operation.
 * Cache evicted on freeze/close.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountReadService {

    private final AccountsRepository accountRepository;
    private final AccountAccessPolicy accessPolicy;

    /**
     * Ownership-guarded balance read (customer-facing endpoint).
     *
     * The ownership check MUST run on every request, so it cannot live inside the
     * {@link #getBalance(String)} method below — that one is {@code @Cacheable} and a
     * cache hit would short-circuit the method (and skip the check). We load the
     * account here (which also yields the balance) and guard before returning.
     * Owner or a privileged role → 200; any other caller → ACCT_002 (404), no leak.
     */
    @Transactional(readOnly = true)
    public BalanceResponse getBalanceForCaller(String accountNumber, Jwt jwt) {
        Account account = accountRepository
                .findByAccountNumberAndDeletedAtIsNull(accountNumber)
                .orElseThrow(() -> new AccountException("ACCT_002", "Account not found"));
        accessPolicy.requireReadAccess(account.getKeycloakUserId(), jwt);
        return toBalanceResponse(account);
    }

    // Read balance from Redis — falls through to DB on cache miss.
    // NOTE: unguarded data read — must only be called after an ownership check
    // (see getBalanceForCaller) or from trusted internal/write paths.
    @Cacheable(value = "account-balance", key = "#accountNumber")
    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String accountNumber) {
        log.debug("Cache miss for account: {} — fetching from DB", accountNumber.replaceAll("[\r\n]", "_"));
        Account account = accountRepository
                .findByAccountNumberAndDeletedAtIsNull(accountNumber)
                .orElseThrow(() -> new AccountException("ACCT_002", "Account not found"));
        return toBalanceResponse(account);
    }

    // Update cache on every balance write
    @CachePut(value = "account-balance", key = "#account.accountNumber")
    public BalanceResponse updateBalanceCache(Account account) {
        log.debug("Updating balance cache for: {}", account.getAccountNumber());
        return toBalanceResponse(account);
    }

    // Evict on freeze/close — stale balance must not be served
    @CacheEvict(value = "account-balance", key = "#accountNumber")
    public void evictBalanceCache(String accountNumber) {
        log.debug("Balance cache evicted for: {}", accountNumber);
    }

    private BalanceResponse toBalanceResponse(Account a) {
        return new BalanceResponse(
                a.getAccountNumber(),
                a.getBalance(),
                a.getBalance().subtract(a.getMinimumBalance()),
                a.getCurrency(),
                a.getStatus().name(),
                LocalDateTime.now()
        );
    }
}
