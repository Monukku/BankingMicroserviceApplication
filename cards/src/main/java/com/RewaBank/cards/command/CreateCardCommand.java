package com.RewaBank.cards.command;

import lombok.Builder;
import lombok.Data;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

@Data
@Builder
public class CreateCardCommand {

    @TargetAggregateIdentifier
    private Long cardNumber;

    private String mobileNumber;

    private String cardType;

    private int totalLimit;

    private int amountUsed;

    private int availableAmount;

    private boolean activeSw;
}
