package com.RewaBank.loans.command;

import lombok.Builder;
import lombok.Data;
import org.axonframework.modelling.command.TargetAggregateIdentifier;


@Builder
@Data
public class DeleteLoanCommand {
    @TargetAggregateIdentifier
    private Long loanNumber;
    private boolean activeSw;


}
