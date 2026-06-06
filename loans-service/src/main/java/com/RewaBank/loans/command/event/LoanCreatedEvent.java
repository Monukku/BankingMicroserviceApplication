package com.RewaBank.loans.command.event;

import lombok.Data;

@Data
public class LoanCreatedEvent {

    private Long loanNumber;

    private String mobileNumber;

    private String loanType;

    private int totalLoan;

    private String loanStatus;

    private int amountPaid;

    private  int outstandingAmount;

    private boolean activeSw;
}
