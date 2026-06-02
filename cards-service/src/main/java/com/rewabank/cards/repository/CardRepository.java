package com.rewabank.cards.repository;

import com.rewabank.cards.entity.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CardRepository extends JpaRepository<Card, UUID> {

    List<Card> findByKeycloakUserIdAndStatusNot(
            String keycloakUserId, Card.CardStatus status);

    List<Card> findByAccountIdAndStatusNot(
            UUID accountId, Card.CardStatus status);

    Optional<Card> findByIdAndKeycloakUserId(UUID id, String keycloakUserId);

    // Cards expiring in next 30 days — for expiry alert scheduler
    @Query("""
        SELECT c FROM Card c
        WHERE c.status = 'ACTIVE'
        AND c.expiryDate BETWEEN :today AND :thirtyDaysLater
        AND c.expiryAlertSent = false
        """)
    List<Card> findCardsExpiringIn30Days(
            LocalDate today, LocalDate thirtyDaysLater);

    // Find active cards by account — for fraud auto-block
    List<Card> findByAccountIdAndStatus(
            UUID accountId, Card.CardStatus status);

    @Query("""
    SELECT c FROM Card c
    WHERE c.status = 'ACTIVE'
    AND c.expiryDate < :today
    """)
    List<Card> findActiveExpiredCards(LocalDate today);
}