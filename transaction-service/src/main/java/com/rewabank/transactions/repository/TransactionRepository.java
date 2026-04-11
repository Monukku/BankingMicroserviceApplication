package com.rewabank.transactions.repository;

import com.rewabank.transactions.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository
        extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    Optional<Transaction> findById(UUID id);

    Page<Transaction> findByKeycloakUserIdOrderByCreatedAtDesc(
            String keycloakUserId, Pageable pageable);

    Page<Transaction> findBySourceAccountIdOrDestinationAccountIdOrderByCreatedAtDesc(
            UUID sourceAccountId, UUID destinationAccountId, Pageable pageable);

    @Query("""
        SELECT t FROM Transaction t
        WHERE t.id = :id
        AND t.keycloakUserId = :keycloakUserId
        """)
    Optional<Transaction> findByIdAndUser(UUID id, String keycloakUserId);
}
