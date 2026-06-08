package com.rewabank.cards.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.cards.client.FraudFeignClient;
import com.rewabank.cards.client.NotificationFeignClient;
import com.rewabank.cards.entity.Card;
import com.rewabank.cards.entity.CardTransaction;
import com.rewabank.cards.entity.OutboxEvent;
import com.rewabank.cards.exception.CardException;
import com.rewabank.cards.repository.CardRepository;
import com.rewabank.cards.repository.CardTransactionRepository;
import com.rewabank.cards.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CardTransactionService {

    private final CardTransactionRepository transactionRepository;
    private final CardRepository cardRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final FraudFeignClient fraudFeignClient;
    private final NotificationFeignClient notificationFeignClient;
    private final ObjectMapper objectMapper;

    /**
     * Record a transaction attempt
     */
    public CardTransaction recordTransaction(
            UUID cardId,
            BigDecimal amount,
            String currency,
            String description,
            String idempotencyKey) {

        log.info("Recording transaction for card: {}, amount: {}, currency: {}", 
                cardId, amount, currency);

        // Check idempotency
        var existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Transaction already exists with idempotency key: {}", idempotencyKey);
            return existing.get();
        }

        // Verify card exists
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new CardException("CARD_NOT_FOUND", "Card not found: " + cardId));

        if (!card.isActive()) {
            throw new CardException("CARD_INACTIVE", "Card is not active");
        }

        // Create transaction
        CardTransaction transaction = CardTransaction.builder()
                .cardId(cardId)
                .amount(amount)
                .currency(currency != null ? currency : "INR")
                .description(description)
                .status(CardTransaction.TransactionStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .createdAt(LocalDateTime.now())
                .build();

        CardTransaction saved = transactionRepository.save(transaction);
        publishEvent(saved, "TRANSACTION_INITIATED");

        log.info("Transaction recorded: {}", saved.getId());
        return saved;
    }

    /**
     * Authorize a transaction
     */
    public CardTransaction authorizeTransaction(UUID transactionId) {
        log.info("Authorizing transaction: {}", transactionId);

        CardTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new CardException("TXN_NOT_FOUND", "Transaction not found: " + transactionId));

        Card card = cardRepository.findById(transaction.getCardId())
                .orElseThrow(() -> new CardException("CARD_NOT_FOUND", "Card not found"));

        // Validate transaction limits
        validateTransaction(card, transaction);

        // Validate against fraud patterns
        validateFraud(transaction, card);

        transaction.setStatus(CardTransaction.TransactionStatus.AUTHORIZED);
        transaction.setAuthorizedAt(LocalDateTime.now());

        CardTransaction updated = transactionRepository.save(transaction);
        publishEvent(updated, "TRANSACTION_AUTHORIZED");

        log.info("Transaction {} authorized", transactionId);
        return updated;
    }

    /**
     * Settle/complete a transaction
     */
    public CardTransaction settleTransaction(UUID transactionId) {
        log.info("Settling transaction: {}", transactionId);

        CardTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new CardException("TXN_NOT_FOUND", "Transaction not found: " + transactionId));

        if (transaction.getStatus() != CardTransaction.TransactionStatus.AUTHORIZED) {
            throw new CardException("TXN_INVALID_STATE", "Transaction is not authorized");
        }

        transaction.setStatus(CardTransaction.TransactionStatus.SETTLED);
        transaction.setSettledAt(LocalDateTime.now());

        CardTransaction updated = transactionRepository.save(transaction);
        publishEvent(updated, "TRANSACTION_SETTLED");

        log.info("Transaction {} settled", transactionId);
        return updated;
    }

    /**
     * Decline a transaction
     */
    public CardTransaction declineTransaction(UUID transactionId, String reason) {
        log.info("Declining transaction: {}, reason: {}", transactionId, reason);

        CardTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new CardException("TXN_NOT_FOUND", "Transaction not found: " + transactionId));

        transaction.setStatus(CardTransaction.TransactionStatus.DECLINED);
        transaction.setDeclinedAt(LocalDateTime.now());
        transaction.setDeclinedReason(reason);

        CardTransaction updated = transactionRepository.save(transaction);
        publishEvent(updated, "TRANSACTION_DECLINED");

        log.info("Transaction {} declined", transactionId);
        return updated;
    }

    /**
     * Reverse/refund a transaction
     */
    public CardTransaction reverseTransaction(UUID transactionId, String reason) {
        log.info("Reversing transaction: {}, reason: {}", transactionId, reason);

        CardTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new CardException("TXN_NOT_FOUND", "Transaction not found: " + transactionId));

        transaction.setStatus(CardTransaction.TransactionStatus.REVERSED);
        transaction.setReversedAt(LocalDateTime.now());
        transaction.setReverseReason(reason);

        CardTransaction updated = transactionRepository.save(transaction);
        publishEvent(updated, "TRANSACTION_REVERSED");

        log.info("Transaction {} reversed", transactionId);
        return updated;
    }

    /**
     * Get transaction history for a card with pagination
     */
    public Page<CardTransaction> getTransactionHistory(UUID cardId, Pageable pageable) {
        log.debug("Fetching transaction history for card: {}", cardId);
        
        // Verify card exists
        cardRepository.findById(cardId)
                .orElseThrow(() -> new CardException("CARD_NOT_FOUND", "Card not found: " + cardId));
        
        return transactionRepository.findByCardId(cardId, pageable);
    }

    /**
     * Validate transaction against card limits
     */
    private void validateTransaction(Card card, CardTransaction transaction) {
        // Daily limit check
        BigDecimal dailyTotal = transactionRepository
                .findByCardId(card.getId())
                .stream()
                .filter(t -> isToday(t.getCreatedAt()))
                .filter(t -> t.getStatus() == CardTransaction.TransactionStatus.SETTLED)
                .map(CardTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (dailyTotal.add(transaction.getAmount()).compareTo(card.getDailyLimit()) > 0) {
            throw new CardException("LIMIT_EXCEEDED", "Daily limit exceeded");
        }

        // Monthly limit check
        BigDecimal monthlyTotal = transactionRepository
                .findByCardId(card.getId())
                .stream()
                .filter(t -> isThisMonth(t.getCreatedAt()))
                .filter(t -> t.getStatus() == CardTransaction.TransactionStatus.SETTLED)
                .map(CardTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (monthlyTotal.add(transaction.getAmount()).compareTo(card.getMonthlyLimit()) > 0) {
            throw new CardException("LIMIT_EXCEEDED", "Monthly limit exceeded");
        }

        // International check
        if (!card.getInternationalEnabled() && transaction.getIsInternational()) {
            throw new CardException("TXN_NOT_ALLOWED", "International transactions not enabled");
        }

        // Online check
        if (!card.getOnlineEnabled() && CardTransaction.TransactionType.ONLINE == transaction.getTransactionType()) {
            throw new CardException("TXN_NOT_ALLOWED", "Online transactions not enabled");
        }
    }

    /**
     * Helper to check if transaction is from today
     */
    private boolean isToday(LocalDateTime dateTime) {
        LocalDateTime now = LocalDateTime.now();
        return dateTime.toLocalDate().equals(now.toLocalDate());
    }

    /**
     * Helper to check if transaction is from this month
     */
    private boolean isThisMonth(LocalDateTime dateTime) {
        LocalDateTime now = LocalDateTime.now();
        return dateTime.getYear() == now.getYear() && 
               dateTime.getMonth() == now.getMonth();
    }

    /**
     * Validate transaction against fraud patterns
     */
    private void validateFraud(CardTransaction transaction, Card card) {
        try {
            var fraudRequest = new FraudFeignClient.TransactionValidationRequest(
                    card.getId(),
                    card.getCustomerId(),
                    transaction.getAmount(),
                    transaction.getCurrency(),
                    transaction.getMerchantName() != null ? transaction.getMerchantName() : "UNKNOWN",
                    transaction.getTransactionType().name(),
                    transaction.getIsInternational(),
                    transaction.getChannel() != null ? transaction.getChannel().name() : "UNKNOWN"
            );

            var fraudResponse = fraudFeignClient.validateTransaction(fraudRequest);
            
            @SuppressWarnings("unchecked")
            Boolean approved = (Boolean) fraudResponse.getOrDefault("approved", true);
            
            if (!approved) {
                String reason = (String) fraudResponse.getOrDefault("reason", "Fraud check failed");
                log.warn("Transaction {} failed fraud check: {}", transaction.getId(), reason);
                throw new CardException("FRAUD_DETECTED", "Transaction declined by fraud detection: " + reason);
            }

            Integer fraudScore = ((Number) fraudResponse.getOrDefault("fraudScore", 0)).intValue();
            transaction.setFraudScore(fraudScore);
            log.info("Transaction {} fraud score: {}", transaction.getId(), fraudScore);

        } catch (Exception e) {
            // Log but don't fail - fraud-ms might be down
            log.warn("Could not validate fraud for transaction: {}", transaction.getId(), e);
            // In production, decide if you want to fail or allow with warning
            // For now, we log and continue
        }
    }

    /**
     * Publish transaction event to outbox
     */
    private void publishEvent(CardTransaction transaction, String eventType) {
        try {
            OutboxEvent event = OutboxEvent.builder()
                    .aggregateId(transaction.getId().toString())
                    .eventType(eventType)
                    .payload(objectMapper.writeValueAsString(transaction))
                    .createdAt(LocalDateTime.now())
                    .published(false)
                    .build();

            outboxEventRepository.save(event);
            log.info("Published event: {} for transaction: {}", eventType, transaction.getId());
        } catch (Exception e) {
            log.error("Failed to publish event", e);
        }
    }
}
