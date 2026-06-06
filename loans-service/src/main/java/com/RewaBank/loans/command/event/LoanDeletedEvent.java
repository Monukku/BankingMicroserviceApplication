package com.RewaBank.loans.command.event;

import lombok.Data;

@Data
public class LoanDeletedEvent {
    private boolean activeSw;
    private Long loanNumber;
}
