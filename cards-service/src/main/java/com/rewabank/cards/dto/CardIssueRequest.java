package com.rewabank.cards.dto;

import com.rewabank.cards.entity.Card;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CardIssueRequest(

        @NotNull(message = "Account ID is required")
        UUID accountId,

        @NotNull(message = "Card type is required")
        Card.CardType cardType,

        @NotNull(message = "Card network is required")
        Card.CardNetwork cardNetwork,

        // Physical card chip limit is 26 characters
        @Size(max = 26, message = "Name on card must not exceed 26 characters")
        String nameOnCard   // defaults to account holder name if null
) {}