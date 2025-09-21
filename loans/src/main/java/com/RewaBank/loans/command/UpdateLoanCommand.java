package com.RewaBank.loans.command;

import lombok.Builder;
import lombok.Data;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

@Builder
@Data
public class UpdateLoanCommand {
    @TargetAggregateIdentifier
    private Long loanNumber;

    private String mobileNumber;

    private String loanType;

    private int totalLoan;

    private String loanStatus;

    private int amountPaid;

    private  int outstandingAmount;

    private boolean activeSw;


}
