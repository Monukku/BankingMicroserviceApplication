package com.rewabank.cards.service;

import com.rewabank.cards.dto.*;
import com.rewabank.cards.entity.Card;
import com.rewabank.cards.exception.CardException;
import com.rewabank.cards.kafka.CardEventProducer;
import com.rewabank.cards.repository.CardRepository;
import com.rewabank.cards.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CardService {

    private final CardRepository    cardRepository;
    private final CardEventProducer eventProducer;
    private final EncryptionUtil    encryptionUtil;

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(10);
    private final SecureRandom secureRandom = new SecureRandom();

    // ── Issue card ────────────────────────────────────────────────────────────
    @Transactional
    public CardResponse issueCard(String keycloakUserId,
                                  CardIssueRequest request) {
        // Generate 16-digit card number
        String cardNumber = generateCardNumber(request.cardNetwork());
        String lastFour   = cardNumber.substring(cardNumber.length() - 4);

        // Generate CVV
        String cvv     = String.format("%03d",
                secureRandom.nextInt(1000));
        String cvvHash = bcrypt.encode(cvv);

        Card card = Card.builder()
                .keycloakUserId(keycloakUserId)
                .accountId(request.accountId())
                .cardNumberEncrypted(encryptionUtil.encrypt(cardNumber))
                .cardLastFour(lastFour)
                .cardType(request.cardType())
                .cardNetwork(request.cardNetwork())
                .status(Card.CardStatus.PENDING)  // FIXED: was ACTIVE
                .expiryDate(LocalDate.now().plusYears(5))
                .cvvHash(cvvHash)
                .nameOnCard(request.nameOnCard() != null
                        ? request.nameOnCard() : "CARD HOLDER")
                .build();

        cardRepository.save(card);

        eventProducer.publishCardIssued(
                card.getId().toString(),
                keycloakUserId,
                request.accountId().toString(),
                request.cardType().name(),
                lastFour
        );

        log.info("Card issued: **** **** **** {} for user: {}",
                lastFour, keycloakUserId);

        return toResponse(card);
    }

    // ── Block card (OTP required — RBI mandate) ───────────────────────────────
    @Transactional
    public CardResponse blockCard(UUID cardId, String keycloakUserId,
                                  CardBlockRequest request) {
        Card card = findCardForUser(cardId, keycloakUserId);

        if (!card.canBlock()) {
            throw new CardException("CARD_002",
                    "Card cannot be blocked from status: " + card.getStatus());
        }

        // OTP verified by caller before reaching this method
        // In production: Auth MS OTP verify called from controller

        card.setStatus(Card.CardStatus.BLOCKED);
        card.setBlockReason(request.reason());
        card.setBlockedAt(LocalDateTime.now());
        card.setBlockedBy(keycloakUserId);
        cardRepository.save(card);

        eventProducer.publishCardBlocked(
                card.getId().toString(),
                keycloakUserId,
                card.getAccountId().toString(),
                request.reason()
        );

        log.warn("Card blocked: **** **** **** {} by: {}",
                card.getCardLastFour(), keycloakUserId);

        return toResponse(card);
    }

    // ── Unblock card (OTP required — RBI mandate) ─────────────────────────────
    @Transactional
    public CardResponse unblockCard(UUID cardId, String keycloakUserId) {
        Card card = findCardForUser(cardId, keycloakUserId);

        if (!card.canUnblock()) {
            throw new CardException("CARD_002",
                    "Card cannot be unblocked from status: " + card.getStatus());
        }

        card.setStatus(Card.CardStatus.ACTIVE);
        card.setBlockReason(null);
        card.setBlockedAt(null);
        card.setBlockedBy(null);
        cardRepository.save(card);

        log.info("Card unblocked: **** **** **** {}",
                card.getCardLastFour());

        return toResponse(card);
    }

    // ── Auto-block from fraud alert ───────────────────────────────────────────
    @Transactional
    public void autoBlockByAccountId(UUID accountId, String reason) {
        List<Card> activeCards = cardRepository
                .findByAccountIdAndStatus(accountId, Card.CardStatus.ACTIVE);

        activeCards.forEach(card -> {
            card.setStatus(Card.CardStatus.BLOCKED);
            card.setBlockReason("AUTO-BLOCKED: " + reason);
            card.setBlockedAt(LocalDateTime.now());
            card.setBlockedBy("FRAUD_SYSTEM");
            cardRepository.save(card);

            eventProducer.publishCardBlocked(
                    card.getId().toString(),
                    card.getKeycloakUserId(),
                    accountId.toString(),
                    "Auto-blocked due to fraud alert"
            );

            log.warn("Card auto-blocked: **** **** **** {} fraud alert",
                    card.getCardLastFour());
        });
    }

    // ── Get my cards ──────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<CardResponse> getMyCards(String keycloakUserId) {
        return cardRepository
                .findByKeycloakUserIdAndStatusNot(
                        keycloakUserId, Card.CardStatus.CANCELLED)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CardResponse getById(UUID cardId, String keycloakUserId) {
        return toResponse(findCardForUser(cardId, keycloakUserId));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private Card findCardForUser(UUID cardId, String keycloakUserId) {
        return cardRepository
                .findByIdAndKeycloakUserId(cardId, keycloakUserId)
                .orElseThrow(() -> new CardException("CARD_001",
                        "Card not found or does not belong to you"));
    }

    private String generateCardNumber(Card.CardNetwork network) {
        // IIN prefix by network
        String prefix = switch (network) {
            case VISA       -> "4";
            case MASTERCARD -> "51";
            case RUPAY      -> "60";
        };

        StringBuilder sb = new StringBuilder(prefix);
        while (sb.length() < 15) {
            sb.append(secureRandom.nextInt(10));
        }
        // Luhn check digit
        sb.append(luhnCheckDigit(sb.toString()));
        return sb.toString();
    }

    private int luhnCheckDigit(String number) {
        int sum = 0;
        boolean alternate = true;
        for (int i = number.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(String.valueOf(number.charAt(i)));
            if (alternate) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            alternate = !alternate;
        }
        return (10 - (sum % 10)) % 10;
    }

    private CardResponse toResponse(Card c) {
        return new CardResponse(
                c.getId(),
                c.getAccountId(),
                encryptionUtil.maskCardNumber(c.getCardLastFour()),
                c.getCardType(),
                c.getCardNetwork(),
                c.getStatus(),
                c.getNameOnCard(),
                c.getExpiryDate(),
                c.getBlockReason(),
                c.getActivatedAt(),
                c.getCreatedAt()
        );
    }
}