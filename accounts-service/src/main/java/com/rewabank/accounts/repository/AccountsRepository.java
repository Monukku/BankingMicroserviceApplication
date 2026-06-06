package com.rewabank.accounts.repository;

import com.rewabank.accounts.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
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
    @Lock(LockModeType.PESSIMISTIC_WRITE)
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

    boolean existsByAccountNumberAndDeletedAtIsNull(String accountNumber);

    boolean existsByCustomerIdAndAccountTypeAndStatusAndDeletedAtIsNull(
            UUID customerId,
            Account.AccountType accountType,
            Account.AccountStatus status);
}
