package com.RewaBank.cards.command.event;

import lombok.Data;

@Data
public class CardDeletedEvent {
    private boolean activeSw;
    private Long cardNumber;
}
