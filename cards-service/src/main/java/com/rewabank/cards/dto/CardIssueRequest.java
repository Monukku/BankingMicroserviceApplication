package com.rewabank.cards.dto;

import com.rewabank.cards.entity.Card;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CardIssueRequest(

        @NotNull(message = "Account ID is required")
        UUID accountId,

        @NotNull(message = "Card type is required")
        Card.CardType cardType,

        @NotNull(message = "Card network is required")
        Card.CardNetwork cardNetwork,

        String nameOnCard   // defaults to account holder name if null
) {}