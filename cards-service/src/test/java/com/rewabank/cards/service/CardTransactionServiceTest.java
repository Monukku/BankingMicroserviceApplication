package com.rewabank.cards.service;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CardTransactionServiceTest {

    @Mock CardTransactionRepository transactionRepository;
    @Mock CardRepository            cardRepository;
    @Mock OutboxEventRepository     outboxEventRepository;
    @Mock FraudFeignClient          fraudFeignClient;
    @Mock NotificationFeignClient   notificationFeignClient;
    @Mock ObjectMapper              objectMapper;

    @InjectMocks CardTransactionService cardTransactionService;

    private UUID cardId;
    private UUID txnId;
    private Card activeCard;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        cardId = UUID.randomUUID();
        txnId  = UUID.randomUUID();

        activeCard = Card.builder()
                .id(cardId)
                .keycloakUserId("kc-user-1")
                .status(Card.CardStatus.ACTIVE)
                .expiryDate(LocalDate.now().plusYears(3))
                .dailyLimit(new BigDecimal("100000"))
                .monthlyLimit(new BigDecimal("500000"))
                .internationalEnabled(true)
                .onlineEnabled(true)
                .build();

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        // Fraud client returns ALLOW by default
        when(fraudFeignClient.getScore(any(), any(), anyString(), anyString()))
                .thenReturn(Map.of("score", 5, "action", "ALLOW"));
    }

    private CardTransaction pendingTxn(UUID id) {
        return CardTransaction.builder()
                .id(id)
                .cardId(cardId)
                .amount(new BigDecimal("5000"))
                .currency("INR")
                .status(CardTransaction.TransactionStatus.PENDING)
                .transactionType(CardTransaction.TransactionType.PURCHASE)
                .channel(CardTransaction.Channel.POS)
                .isInternational(false)
                .idempotencyKey("idem-" + id)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── recordTransaction ─────────────────────────────────────────

    @Test
    void recordTransaction_idempotentKey_returnsExisting() {
        CardTransaction existing = pendingTxn(txnId);
        when(transactionRepository.findByIdempotencyKey("idem-key"))
                .thenReturn(Optional.of(existing));

        CardTransaction result = cardTransactionService.recordTransaction(
                cardId, new BigDecimal("5000"), "INR", "Test", "idem-key");

        assertThat(result).isSameAs(existing);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void recordTransaction_newTransaction_savesAndPublishesOutbox() {
        when(transactionRepository.findByIdempotencyKey(anyString()))
                .thenReturn(Optional.empty());
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(activeCard));
        when(transactionRepository.save(any())).thenAnswer(inv -> {
            CardTransaction t = inv.getArgument(0);
            if (t.getId() == null) t.setId(UUID.randomUUID());
            return t;
        });

        CardTransaction result = cardTransactionService.recordTransaction(
                cardId, new BigDecimal("1000"), "INR", "Grocery", "new-key");

        assertThat(result.getStatus()).isEqualTo(CardTransaction.TransactionStatus.PENDING);
        assertThat(result.getCardId()).isEqualTo(cardId);
        verify(transactionRepository).save(any(CardTransaction.class));
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void recordTransaction_nullCurrency_defaultsToINR() {
        when(transactionRepository.findByIdempotencyKey(anyString()))
                .thenReturn(Optional.empty());
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(activeCard));
        when(transactionRepository.save(any())).thenAnswer(inv -> {
            CardTransaction t = inv.getArgument(0);
            if (t.getId() == null) t.setId(UUID.randomUUID());
            return t;
        });

        CardTransaction result = cardTransactionService.recordTransaction(
                cardId, new BigDecimal("500"), null, "ATM", "key-null");

        assertThat(result.getCurrency()).isEqualTo("INR");
    }

    @Test
    void recordTransaction_cardNotFound_throws() {
        when(transactionRepository.findByIdempotencyKey(anyString()))
                .thenReturn(Optional.empty());
        when(cardRepository.findById(cardId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardTransactionService.recordTransaction(
                cardId, new BigDecimal("500"), "INR", "Test", "key"))
                .isInstanceOf(CardException.class)
                .hasMessageContaining("Card not found");
    }

    @Test
    void recordTransaction_inactiveCard_throws() {
        activeCard.setStatus(Card.CardStatus.BLOCKED);
        when(transactionRepository.findByIdempotencyKey(anyString()))
                .thenReturn(Optional.empty());
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(activeCard));

        assertThatThrownBy(() -> cardTransactionService.recordTransaction(
                cardId, new BigDecimal("500"), "INR", "Test", "key"))
                .isInstanceOf(CardException.class)
                .hasMessageContaining("not active");
    }

    // ── authorizeTransaction ──────────────────────────────────────

    @Test
    void authorizeTransaction_underLimits_setsAuthorized() {
        CardTransaction txn = pendingTxn(txnId);
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(activeCard));
        when(transactionRepository.findByCardId(cardId)).thenReturn(List.of());
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CardTransaction result = cardTransactionService.authorizeTransaction(txnId);

        assertThat(result.getStatus()).isEqualTo(CardTransaction.TransactionStatus.AUTHORIZED);
        assertThat(result.getAuthorizedAt()).isNotNull();
    }

    @Test
    void authorizeTransaction_notFound_throws() {
        when(transactionRepository.findById(txnId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardTransactionService.authorizeTransaction(txnId))
                .isInstanceOf(CardException.class)
                .hasMessageContaining("Transaction not found");
    }

    @Test
    void authorizeTransaction_cardNotFound_throws() {
        CardTransaction txn = pendingTxn(txnId);
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));
        when(cardRepository.findById(cardId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardTransactionService.authorizeTransaction(txnId))
                .isInstanceOf(CardException.class)
                .hasMessageContaining("Card not found");
    }

    @Test
    void authorizeTransaction_dailyLimitExceeded_throws() {
        activeCard.setDailyLimit(new BigDecimal("50000"));
        CardTransaction txn = pendingTxn(txnId);
        txn.setAmount(new BigDecimal("10000"));

        // Previous settled transaction today for 45,000
        CardTransaction prev = CardTransaction.builder()
                .cardId(cardId).amount(new BigDecimal("45000"))
                .status(CardTransaction.TransactionStatus.SETTLED)
                .transactionType(CardTransaction.TransactionType.PURCHASE)
                .channel(CardTransaction.Channel.POS)
                .createdAt(LocalDateTime.now())
                .build();

        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(activeCard));
        when(transactionRepository.findByCardId(cardId)).thenReturn(List.of(prev));

        assertThatThrownBy(() -> cardTransactionService.authorizeTransaction(txnId))
                .isInstanceOf(CardException.class)
                .hasMessageContaining("Daily limit exceeded");
    }

    @Test
    void authorizeTransaction_monthlyLimitExceeded_throws() {
        activeCard.setMonthlyLimit(new BigDecimal("50000"));
        CardTransaction txn = pendingTxn(txnId);
        txn.setAmount(new BigDecimal("10000"));

        // Previous settled transaction this month for 45,000 (but old day)
        CardTransaction prev = CardTransaction.builder()
                .cardId(cardId).amount(new BigDecimal("45000"))
                .status(CardTransaction.TransactionStatus.SETTLED)
                .transactionType(CardTransaction.TransactionType.PURCHASE)
                .channel(CardTransaction.Channel.POS)
                .createdAt(LocalDateTime.now().minusDays(5)) // earlier this month
                .build();

        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(activeCard));
        when(transactionRepository.findByCardId(cardId)).thenReturn(List.of(prev));

        assertThatThrownBy(() -> cardTransactionService.authorizeTransaction(txnId))
                .isInstanceOf(CardException.class)
                .hasMessageContaining("Monthly limit exceeded");
    }

    @Test
    void authorizeTransaction_internationalDisabled_internationalTxn_throws() {
        activeCard.setInternationalEnabled(false);
        CardTransaction txn = pendingTxn(txnId);
        txn.setIsInternational(true);

        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(activeCard));
        when(transactionRepository.findByCardId(cardId)).thenReturn(List.of());

        assertThatThrownBy(() -> cardTransactionService.authorizeTransaction(txnId))
                .isInstanceOf(CardException.class)
                .hasMessageContaining("International transactions not enabled");
    }

    @Test
    void authorizeTransaction_fraudBlocked_throws() {
        when(fraudFeignClient.getScore(any(), any(), anyString(), anyString()))
                .thenReturn(Map.of("score", 95, "action", "BLOCK", "reason", "Suspicious pattern"));

        CardTransaction txn = pendingTxn(txnId);
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(activeCard));
        when(transactionRepository.findByCardId(cardId)).thenReturn(List.of());

        assertThatThrownBy(() -> cardTransactionService.authorizeTransaction(txnId))
                .isInstanceOf(CardException.class)
                .hasMessageContaining("fraud");
    }

    @Test
    void authorizeTransaction_onlineDisabled_onlineTxn_throws() {
        activeCard.setOnlineEnabled(false);
        CardTransaction txn = pendingTxn(txnId);
        txn.setTransactionType(CardTransaction.TransactionType.ONLINE);

        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(activeCard));
        when(transactionRepository.findByCardId(cardId)).thenReturn(List.of());

        assertThatThrownBy(() -> cardTransactionService.authorizeTransaction(txnId))
                .isInstanceOf(CardException.class)
                .hasMessageContaining("Online transactions not enabled");
    }

    // ── settleTransaction ─────────────────────────────────────────

    @Test
    void settleTransaction_authorized_setsSettledAndTimestamp() {
        CardTransaction txn = pendingTxn(txnId);
        txn.setStatus(CardTransaction.TransactionStatus.AUTHORIZED);
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CardTransaction result = cardTransactionService.settleTransaction(txnId);

        assertThat(result.getStatus()).isEqualTo(CardTransaction.TransactionStatus.SETTLED);
        assertThat(result.getSettledAt()).isNotNull();
    }

    @Test
    void settleTransaction_notAuthorized_throws() {
        CardTransaction txn = pendingTxn(txnId); // PENDING, not AUTHORIZED
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> cardTransactionService.settleTransaction(txnId))
                .isInstanceOf(CardException.class)
                .hasMessageContaining("not authorized");
    }

    @Test
    void settleTransaction_notFound_throws() {
        when(transactionRepository.findById(txnId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardTransactionService.settleTransaction(txnId))
                .isInstanceOf(CardException.class)
                .hasMessageContaining("Transaction not found");
    }

    // ── declineTransaction ────────────────────────────────────────

    @Test
    void declineTransaction_setsDeclinedStatusAndReason() {
        CardTransaction txn = pendingTxn(txnId);
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CardTransaction result = cardTransactionService.declineTransaction(txnId, "Insufficient funds");

        assertThat(result.getStatus()).isEqualTo(CardTransaction.TransactionStatus.DECLINED);
        assertThat(result.getDeclinedReason()).isEqualTo("Insufficient funds");
        assertThat(result.getDeclinedAt()).isNotNull();
    }

    @Test
    void declineTransaction_notFound_throws() {
        when(transactionRepository.findById(txnId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardTransactionService.declineTransaction(txnId, "reason"))
                .isInstanceOf(CardException.class)
                .hasMessageContaining("Transaction not found");
    }

    // ── reverseTransaction ────────────────────────────────────────

    @Test
    void reverseTransaction_setsReversedStatusAndReason() {
        CardTransaction txn = pendingTxn(txnId);
        txn.setStatus(CardTransaction.TransactionStatus.SETTLED);
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CardTransaction result = cardTransactionService.reverseTransaction(txnId, "Customer dispute");

        assertThat(result.getStatus()).isEqualTo(CardTransaction.TransactionStatus.REVERSED);
        assertThat(result.getReverseReason()).isEqualTo("Customer dispute");
        assertThat(result.getReversedAt()).isNotNull();
    }

    @Test
    void reverseTransaction_notFound_throws() {
        when(transactionRepository.findById(txnId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardTransactionService.reverseTransaction(txnId, "reason"))
                .isInstanceOf(CardException.class)
                .hasMessageContaining("Transaction not found");
    }

    // ── getTransactionHistory ─────────────────────────────────────

    @Test
    void getTransactionHistory_cardExists_returnsPage() {
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(activeCard));
        Page<CardTransaction> page = new PageImpl<>(List.of(pendingTxn(UUID.randomUUID())));
        when(transactionRepository.findByCardId(eq(cardId), any(Pageable.class)))
                .thenReturn(page);

        Page<CardTransaction> result = cardTransactionService
                .getTransactionHistory(cardId, Pageable.ofSize(10));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getTransactionHistory_cardNotFound_throws() {
        when(cardRepository.findById(cardId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardTransactionService
                .getTransactionHistory(cardId, Pageable.ofSize(10)))
                .isInstanceOf(CardException.class)
                .hasMessageContaining("Card not found");
    }
}