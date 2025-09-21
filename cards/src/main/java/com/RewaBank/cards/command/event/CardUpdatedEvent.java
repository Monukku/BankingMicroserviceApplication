package com.RewaBank.cards.command.event;

import lombok.Data;

@Data
public class CardUpdatedEvent {
    private Long cardNumber;

    private String mobileNumber;

    private String cardType;

    private int totalLimit;

    private int amountUsed;

    private int availableAmount;

    private boolean activeSw;
}
