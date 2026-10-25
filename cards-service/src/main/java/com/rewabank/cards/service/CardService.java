package com.rewabank.cards.service;

import com.rewabank.cards.client.AccountsFeignClient;
import com.rewabank.cards.dto.*;
import com.rewabank.cards.entity.Card;
import com.rewabank.cards.exception.CardException;
import com.rewabank.cards.kafka.CardEventProducer;
import com.rewabank.cards.repository.CardRepository;
import com.rewabank.cards.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CardService {

    private final CardRepository     cardRepository;
    private final CardEventProducer  eventProducer;
    private final EncryptionUtil     encryptionUtil;
    private final PasswordEncoder    cvvPasswordEncoder;
    private final AccountsFeignClient accountsFeignClient;

    private final SecureRandom secureRandom = new SecureRandom();

    // ── Issue card ────────────────────────────────────────────────────────────
    @Transactional
    public CardResponse issueCard(String keycloakUserId,
                                  CardIssueRequest request,
                                  String idempotencyKey) {
        // Return existing card if idempotency key already used
        if (idempotencyKey != null) {
            Optional<Card> existing = cardRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }

        // Verify the account belongs to this user
        try {
            Map<String, Object> account = accountsFeignClient.getAccount(request.accountId());
            Object accountOwner = account.get("keycloakUserId");
            log.info("Account ownership check — expected: {}, got: {}", keycloakUserId, accountOwner);
            if (!keycloakUserId.equals(accountOwner)) {
                throw new CardException("CARD_003",
                        "Account does not belong to the requesting user");
            }
        } catch (CardException e) {
            throw e;
        } catch (Exception e) {
            log.error("Feign call to accounts failed: {} — {}", e.getClass().getSimpleName(), e.getMessage());
            throw new CardException("CARD_003",
                    "Could not verify account ownership: " + e.getMessage());
        }

        // Generate 16-digit card number
        String cardNumber = generateCardNumber(request.cardNetwork());
        String lastFour   = cardNumber.substring(cardNumber.length() - 4);

        // Generate CVV
        String cvv     = String.format("%03d",
                secureRandom.nextInt(1000));
        String cvvHash = cvvPasswordEncoder.encode(cvv);

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
                .idempotencyKey(idempotencyKey)
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

    // ── Activate card (Teller physically hands card to customer) ─────────────
    @Transactional
    public CardResponse activateCard(UUID cardId, String staffKeycloakUserId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new CardException("CARD_001", "Card not found"));

        if (!card.canActivate()) {
            throw new CardException("CARD_002",
                    "Card cannot be activated from status: " + card.getStatus());
        }

        card.setStatus(Card.CardStatus.ACTIVE);
        card.setActivatedAt(LocalDateTime.now());
        cardRepository.save(card);

        eventProducer.publishCardActivated(
                card.getId().toString(),
                card.getKeycloakUserId(),
                card.getAccountId().toString(),
                card.getCardLastFour()
        );

        log.info("Card activated: **** **** **** {} by staff: {}",
                card.getCardLastFour(), staffKeycloakUserId);

        return toResponse(card);
    }

    // ── Block card ────────────────────────────────────────────────────────────
    // Customer: OTP required (RBI mandate). Staff (TELLER+): reason only, no OTP.
    @Transactional
    public CardResponse blockCard(UUID cardId, String callerKeycloakUserId,
                                  CardBlockRequest request, boolean isStaff) {
        Card card = isStaff
                ? cardRepository.findById(cardId)
                        .orElseThrow(() -> new CardException("CARD_001", "Card not found"))
                : findCardForUser(cardId, callerKeycloakUserId);

        if (!card.canBlock()) {
            throw new CardException("CARD_002",
                    "Card cannot be blocked from status: " + card.getStatus());
        }

        // Customer must supply OTP (RBI mandate); staff do not
        if (!isStaff && (request.otp() == null || request.otp().isBlank())) {
            throw new CardException("CARD_002", "OTP is required to block your card");
        }

        card.setStatus(Card.CardStatus.BLOCKED);
        card.setBlockReason(request.reason() != null ? request.reason()
                : (isStaff ? "Blocked by branch staff" : "Blocked by cardholder"));
        card.setBlockedAt(LocalDateTime.now());
        card.setBlockedBy(callerKeycloakUserId);
        cardRepository.save(card);

        eventProducer.publishCardBlocked(
                card.getId().toString(),
                card.getKeycloakUserId(),
                card.getAccountId().toString(),
                card.getBlockReason()
        );

        log.warn("Card blocked: **** **** **** {} by: {} (staff={})",
                card.getCardLastFour(), callerKeycloakUserId, isStaff);

        return toResponse(card);
    }

    // ── Unblock card ──────────────────────────────────────────────────────────
    @Transactional
    public CardResponse unblockCard(UUID cardId, String callerKeycloakUserId,
                                    boolean isStaff) {
        Card card = isStaff
                ? cardRepository.findById(cardId)
                        .orElseThrow(() -> new CardException("CARD_001", "Card not found"))
                : findCardForUser(cardId, callerKeycloakUserId);

        if (!card.canUnblock()) {
            throw new CardException("CARD_002",
                    "Card cannot be unblocked from status: " + card.getStatus());
        }

        card.setStatus(Card.CardStatus.ACTIVE);
        card.setBlockReason(null);
        card.setBlockedAt(null);
        card.setBlockedBy(null);
        cardRepository.save(card);

        log.info("Card unblocked: **** **** **** {} by: {}",
                card.getCardLastFour(), callerKeycloakUserId);

        return toResponse(card);
    }

    // ── Cancel card permanently (BranchManager+, compliance/fraud) ───────────
    @Transactional
    public CardResponse cancelCard(UUID cardId, String staffKeycloakUserId, String reason) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new CardException("CARD_001", "Card not found"));

        if (card.getStatus() == Card.CardStatus.CANCELLED) {
            throw new CardException("CARD_002", "Card is already cancelled");
        }

        card.setStatus(Card.CardStatus.CANCELLED);
        card.setBlockReason(reason != null ? reason : "Cancelled by branch manager");
        card.setBlockedAt(LocalDateTime.now());
        card.setBlockedBy(staffKeycloakUserId);
        card.setCancelledAt(LocalDateTime.now());
        cardRepository.save(card);

        eventProducer.publishCardCancelled(
                card.getId().toString(),
                card.getKeycloakUserId(),
                card.getAccountId().toString(),
                card.getBlockReason()
        );

        log.warn("Card cancelled: **** **** **** {} by staff: {}",
                card.getCardLastFour(), staffKeycloakUserId);

        return toResponse(card);
    }

    // ── Get cards for a customer (staff view) ─────────────────────────────────
    @Transactional(readOnly = true)
    public List<CardResponse> getCardsByCustomer(UUID customerId) {
        return cardRepository.findByCustomerId(customerId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CardResponse> getCardsByKeycloakUserId(String keycloakUserId) {
        return cardRepository.findByKeycloakUserIdAndStatusNot(keycloakUserId, Card.CardStatus.CANCELLED)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Get all pending cards (Teller activation queue) ───────────────────────
    @Transactional(readOnly = true)
    public List<CardResponse> getAllCards() {
        return cardRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<CardResponse> getPendingCards() {
        return cardRepository.findByStatus(Card.CardStatus.PENDING)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
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
        Card card = findCardForUser(cardId, keycloakUserId);
        if (card.getStatus() == Card.CardStatus.CANCELLED) {
            throw new CardException("CARD_001", "Card not found or does not belong to you");
        }
        return toResponse(card);
    }

    @Transactional(readOnly = true)
    public CardResponse getByIdForStaff(UUID cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new CardException("CARD_001", "Card not found"));
        return toResponse(card);
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
                c.getCardLastFour(),
                c.getCardType(),
                c.getCardNetwork(),
                c.getStatus(),
                c.getNameOnCard(),
                c.getExpiryDate(),
                c.getBlockReason(),
                c.getBlockedBy(),
                c.getActivatedAt(),
                c.getCreatedAt()
        );
    }
}