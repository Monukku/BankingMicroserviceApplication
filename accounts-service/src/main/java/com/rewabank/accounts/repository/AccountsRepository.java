package com.rewabank.accounts.repository;

import com.rewabank.accounts.entity.Account;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountsRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Account> findByAccountNumberAndDeletedAtIsNull(String accountNumber);

    List<Account> findByKeycloakUserIdAndDeletedAtIsNull(String keycloakUserId);

    List<Account> findByCustomerIdAndDeletedAtIsNull(UUID customerId);

    // Pessimistic lock for balance updates — prevents race conditions
    // 3s timeout: fail fast rather than blocking if a previous saga holds the lock
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT a FROM Account a WHERE a.id = :id AND a.deletedAt IS NULL")
    Optional<Account> findByIdForUpdate(UUID id);

    // Dormancy detection — no transaction in 12 months
    @Query("""
        SELECT a FROM Account a
        WHERE a.status = 'ACTIVE'
        AND a.deletedAt IS NULL
        AND (a.lastTransactionAt IS NULL
             OR a.lastTransactionAt < :cutoffDate)
        """)
    List<Account> findAccountsForDormancy(LocalDateTime cutoffDate);

    // Find PENDING accounts for a customer — for KYC activation
    @Query("""
        SELECT a FROM Account a
        WHERE a.customerId = :customerId
        AND a.status = 'PENDING'
        AND a.deletedAt IS NULL
        """)
    List<Account> findPendingByCustomerId(UUID customerId);

    List<Account> findByStatusAndDeletedAtIsNull(Account.AccountStatus status);

    boolean existsByAccountNumberAndDeletedAtIsNull(String accountNumber);

    boolean existsByCustomerIdAndAccountTypeAndStatusAndDeletedAtIsNull(
            UUID customerId,
            Account.AccountType accountType,
            Account.AccountStatus status);

    // FD maturity — find active FDs due for maturity payout
    @Query("""
        SELECT a FROM Account a
        WHERE a.accountType = 'FIXED_DEPOSIT'
          AND a.status = 'ACTIVE'
          AND a.maturityDate <= :today
          AND a.maturedAt IS NULL
          AND a.deletedAt IS NULL
        """)
    List<Account> findMaturedFixedDeposits(LocalDate today);

    // RD installments — find active RDs due for monthly debit
    @Query("""
        SELECT a FROM Account a
        WHERE a.accountType = 'RECURRING_DEPOSIT'
          AND a.status = 'ACTIVE'
          AND a.nextInstallmentDate <= :today
          AND a.deletedAt IS NULL
        """)
    List<Account> findDueRecurringDeposits(LocalDate today);

    // Count active FDs for a customer (limit: max 5 concurrent FDs)
    @Query("""
        SELECT COUNT(a) FROM Account a
        WHERE a.customerId = :customerId
          AND a.accountType = 'FIXED_DEPOSIT'
          AND a.status = 'ACTIVE'
          AND a.deletedAt IS NULL
        """)
    long countActiveFdsByCustomer(UUID customerId);

    // Count active RDs for a customer (limit: max 3 concurrent RDs)
    @Query("""
        SELECT COUNT(a) FROM Account a
        WHERE a.customerId = :customerId
          AND a.accountType = 'RECURRING_DEPOSIT'
          AND a.status = 'ACTIVE'
          AND a.deletedAt IS NULL
        """)
    long countActiveRdsByCustomer(UUID customerId);
}
