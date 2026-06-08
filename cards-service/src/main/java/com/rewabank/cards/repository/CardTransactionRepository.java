package com.rewabank.cards.repository;

import com.rewabank.cards.entity.CardTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CardTransactionRepository extends JpaRepository<CardTransaction, UUID> {
    List<CardTransaction> findByCardId(UUID cardId);
    Page<CardTransaction> findByCardId(UUID cardId, Pageable pageable);
    Optional<CardTransaction> findByIdempotencyKey(String idempotencyKey);
}

