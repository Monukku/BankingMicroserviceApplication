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

    // Read balance from Redis — falls through to DB on cache miss
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
