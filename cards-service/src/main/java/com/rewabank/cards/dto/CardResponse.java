package com.rewabank.cards.dto;

import com.rewabank.cards.entity.Card;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CardResponse(
        UUID id,
        UUID accountId,
        String maskedCardNumber,    // **** **** **** 1234
        Card.CardType cardType,
        Card.CardNetwork cardNetwork,
        Card.CardStatus status,
        String nameOnCard,
        LocalDate expiryDate,
        String blockReason,
        LocalDateTime activatedAt,
        LocalDateTime createdAt
) {}