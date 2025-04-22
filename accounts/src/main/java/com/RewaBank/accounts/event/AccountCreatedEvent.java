package com.RewaBank.accounts.event;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AccountCreatedEvent {
    private Long accountId;
    private Long accountNumber;
    private BigDecimal balance;

    public AccountCreatedEvent(Long accountId, Long accountNumber, BigDecimal balance) {
        this.accountId = accountId;
        this.accountNumber = accountNumber;
        this.balance = balance;
    }

    // Getters and setters
}
