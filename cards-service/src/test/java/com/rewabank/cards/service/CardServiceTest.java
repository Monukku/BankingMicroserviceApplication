package com.rewabank.cards.service;

import com.rewabank.cards.client.AccountsFeignClient;
import com.rewabank.cards.dto.CardBlockRequest;
import com.rewabank.cards.dto.CardIssueRequest;
import com.rewabank.cards.dto.CardResponse;
import com.rewabank.cards.entity.Card;
import com.rewabank.cards.exception.CardException;
import com.rewabank.cards.kafka.CardEventProducer;
import com.rewabank.cards.repository.CardRepository;
import com.rewabank.cards.util.EncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDate;
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
class CardServiceTest {

    @Mock CardRepository       cardRepository;
    @Mock CardEventProducer    eventProducer;
    @Mock EncryptionUtil       encryptionUtil;
    @Mock AccountsFeignClient  accountsFeignClient;
    @Mock PasswordEncoder      cvvPasswordEncoder;

    @InjectMocks CardService cardService;

    private UUID cardId;
    private UUID accountId;
    private Card activeCard;
    private Card blockedCard;

    @BeforeEach
    void setUp() {
        cardId    = UUID.randomUUID();
        accountId = UUID.randomUUID();

        activeCard = Card.builder()
                .id(cardId)
                .keycloakUserId("kc-user-1")
                .accountId(accountId)
                .cardNumberEncrypted("enc-num")
                .cardLastFour("1234")
                .cardType(Card.CardType.DEBIT)
                .cardNetwork(Card.CardNetwork.VISA)
                .status(Card.CardStatus.ACTIVE)
                .expiryDate(LocalDate.now().plusYears(3))
                .cvvHash("hashed")
                .nameOnCard("Rewa Test")
                .build();

        blockedCard = Card.builder()
                .id(UUID.randomUUID())
                .keycloakUserId("kc-user-1")
                .accountId(accountId)
                .cardLastFour("5678")
                .status(Card.CardStatus.BLOCKED)
                .expiryDate(LocalDate.now().plusYears(3))
                .build();

        when(encryptionUtil.encrypt(anyString())).thenReturn("encrypted-card-number");
        when(encryptionUtil.maskCardNumber(anyString())).thenAnswer(
                inv -> "**** **** **** " + inv.getArgument(0));
        when(cvvPasswordEncoder.encode(anyString())).thenReturn("hashed-cvv");
        when(accountsFeignClient.getAccount(any())).thenReturn(
                Map.of("keycloakUserId", "kc-user-1"));
    }

    // ── issueCard ─────────────────────────────────────────────────

    @Test
    void issueCard_createsCardWithPendingStatusAndPublishesEvent() {
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
            Card c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });

        CardResponse response = cardService.issueCard("kc-user-1",
                new CardIssueRequest(accountId, Card.CardType.DEBIT,
                        Card.CardNetwork.VISA, "Rewa Test"), null);

        assertThat(response.status()).isEqualTo(Card.CardStatus.PENDING);
        verify(encryptionUtil).encrypt(anyString());
        verify(cardRepository).save(any(Card.class));
        verify(eventProducer).publishCardIssued(anyString(), eq("kc-user-1"),
                eq(accountId.toString()), eq("DEBIT"), anyString());
    }

    @Test
    void issueCard_nullNameOnCard_defaultsToCardHolder() {
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
            Card c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });

        cardService.issueCard("kc-user-1",
                new CardIssueRequest(accountId, Card.CardType.CREDIT,
                        Card.CardNetwork.MASTERCARD, null), null);

        verify(cardRepository).save(argThat(c ->
                "CARD HOLDER".equals(c.getNameOnCard())));
    }

    @Test
    void issueCard_expiryDateIsFiveYearsFromNow() {
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
            Card c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });

        cardService.issueCard("kc-user-1",
                new CardIssueRequest(accountId, Card.CardType.DEBIT,
                        Card.CardNetwork.RUPAY, "Rewa Test"), null);

        verify(cardRepository).save(argThat(c ->
                c.getExpiryDate().isAfter(LocalDate.now().plusYears(4))));
    }

    @Test
    void issueCard_visaCardNumber_startsWith4() {
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
            Card c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });

        // Capture the plain card number before encryption
        when(encryptionUtil.encrypt(anyString())).thenAnswer(inv -> {
            String plain = inv.getArgument(0);
            assertThat(plain).startsWith("4");
            return "encrypted";
        });

        cardService.issueCard("kc-user-1",
                new CardIssueRequest(accountId, Card.CardType.DEBIT,
                        Card.CardNetwork.VISA, "Rewa Test"), null);
    }

    @Test
    void issueCard_mastercardNumber_startsWith51() {
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
            Card c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });

        when(encryptionUtil.encrypt(anyString())).thenAnswer(inv -> {
            String plain = inv.getArgument(0);
            assertThat(plain).startsWith("51");
            return "encrypted";
        });

        cardService.issueCard("kc-user-1",
                new CardIssueRequest(accountId, Card.CardType.CREDIT,
                        Card.CardNetwork.MASTERCARD, "Rewa Test"), null);
    }

    @Test
    void issueCard_rupayNumber_startsWith60() {
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
            Card c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });

        when(encryptionUtil.encrypt(anyString())).thenAnswer(inv -> {
            String plain = inv.getArgument(0);
            assertThat(plain).startsWith("60");
            return "encrypted";
        });

        cardService.issueCard("kc-user-1",
                new CardIssueRequest(accountId, Card.CardType.PREPAID,
                        Card.CardNetwork.RUPAY, "Rewa Test"), null);
    }

    // ── blockCard ─────────────────────────────────────────────────

    @Test
    void blockCard_activeCard_setsBlockedAndPublishesEvent() {
        when(cardRepository.findByIdAndKeycloakUserId(cardId, "kc-user-1"))
                .thenReturn(Optional.of(activeCard));
        when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CardResponse response = cardService.blockCard(cardId, "kc-user-1",
                new CardBlockRequest("123456", "Lost card"));

        assertThat(response.status()).isEqualTo(Card.CardStatus.BLOCKED);
        assertThat(activeCard.getBlockReason()).isEqualTo("Lost card");
        assertThat(activeCard.getBlockedBy()).isEqualTo("kc-user-1");
        assertThat(activeCard.getBlockedAt()).isNotNull();
        verify(eventProducer).publishCardBlocked(anyString(), eq("kc-user-1"),
                anyString(), eq("Lost card"));
    }

    @Test
    void blockCard_cardNotActive_throws() {
        Card pendingCard = Card.builder().id(cardId).status(Card.CardStatus.PENDING).build();
        when(cardRepository.findByIdAndKeycloakUserId(cardId, "kc-user-1"))
                .thenReturn(Optional.of(pendingCard));

        assertThatThrownBy(() -> cardService.blockCard(cardId, "kc-user-1",
                new CardBlockRequest("123456", "reason")))
                .isInstanceOf(CardException.class)
                .hasMessageContaining("cannot be blocked");
    }

    @Test
    void blockCard_cardNotFound_throws() {
        when(cardRepository.findByIdAndKeycloakUserId(cardId, "kc-user-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.blockCard(cardId, "kc-user-1",
                new CardBlockRequest("123456", "reason")))
                .isInstanceOf(CardException.class)
                .hasMessageContaining("Card not found");
    }

    // ── unblockCard ───────────────────────────────────────────────

    @Test
    void unblockCard_blockedCard_setsActiveAndClearsFields() {
        UUID blockedId = blockedCard.getId();
        blockedCard.setBlockReason("Lost card");
        blockedCard.setBlockedBy("kc-user-1");
        when(cardRepository.findByIdAndKeycloakUserId(blockedId, "kc-user-1"))
                .thenReturn(Optional.of(blockedCard));
        when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CardResponse response = cardService.unblockCard(blockedId, "kc-user-1");

        assertThat(response.status()).isEqualTo(Card.CardStatus.ACTIVE);
        assertThat(blockedCard.getBlockReason()).isNull();
        assertThat(blockedCard.getBlockedAt()).isNull();
        assertThat(blockedCard.getBlockedBy()).isNull();
    }

    @Test
    void unblockCard_notBlocked_throws() {
        when(cardRepository.findByIdAndKeycloakUserId(cardId, "kc-user-1"))
                .thenReturn(Optional.of(activeCard));

        assertThatThrownBy(() -> cardService.unblockCard(cardId, "kc-user-1"))
                .isInstanceOf(CardException.class)
                .hasMessageContaining("cannot be unblocked");
    }

    // ── autoBlockByAccountId ──────────────────────────────────────

    @Test
    void autoBlockByAccountId_blocksAllActiveCards() {
        Card card2 = Card.builder().id(UUID.randomUUID())
                .keycloakUserId("kc-user-1").accountId(accountId)
                .cardLastFour("9999").status(Card.CardStatus.ACTIVE)
                .expiryDate(LocalDate.now().plusYears(2)).build();

        when(cardRepository.findByAccountIdAndStatus(accountId, Card.CardStatus.ACTIVE))
                .thenReturn(List.of(activeCard, card2));
        when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        cardService.autoBlockByAccountId(accountId, "Fraud detected");

        assertThat(activeCard.getStatus()).isEqualTo(Card.CardStatus.BLOCKED);
        assertThat(card2.getStatus()).isEqualTo(Card.CardStatus.BLOCKED);
        assertThat(activeCard.getBlockedBy()).isEqualTo("FRAUD_SYSTEM");
        verify(cardRepository, times(2)).save(any(Card.class));
        verify(eventProducer, times(2)).publishCardBlocked(anyString(), anyString(),
                anyString(), anyString());
    }

    @Test
    void autoBlockByAccountId_noActiveCards_noSaves() {
        when(cardRepository.findByAccountIdAndStatus(accountId, Card.CardStatus.ACTIVE))
                .thenReturn(List.of());

        cardService.autoBlockByAccountId(accountId, "Fraud");

        verify(cardRepository, never()).save(any());
        verify(eventProducer, never()).publishCardBlocked(anyString(), anyString(),
                anyString(), anyString());
    }

    // ── getMyCards / getById ──────────────────────────────────────

    @Test
    void getMyCards_returnsNonCancelledCards() {
        when(cardRepository.findByKeycloakUserIdAndStatusNot(
                "kc-user-1", Card.CardStatus.CANCELLED))
                .thenReturn(List.of(activeCard, blockedCard));

        List<CardResponse> cards = cardService.getMyCards("kc-user-1");

        assertThat(cards).hasSize(2);
    }

    @Test
    void getById_found_returnsMaskedCardNumber() {
        when(cardRepository.findByIdAndKeycloakUserId(cardId, "kc-user-1"))
                .thenReturn(Optional.of(activeCard));

        CardResponse response = cardService.getById(cardId, "kc-user-1");

        assertThat(response.maskedCardNumber()).startsWith("**** **** **** ");
        assertThat(response.id()).isEqualTo(cardId);
    }

    @Test
    void getById_notFound_throws() {
        UUID unknown = UUID.randomUUID();
        when(cardRepository.findByIdAndKeycloakUserId(unknown, "kc-user-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.getById(unknown, "kc-user-1"))
                .isInstanceOf(CardException.class)
                .hasMessageContaining("Card not found");
    }
}