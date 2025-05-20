package com.RewaBank.accounts.command.event;

import com.RewaBank.accounts.Utility.AccountCategory;
import com.RewaBank.accounts.Utility.AccountStatus;
import com.RewaBank.accounts.Utility.AccountType;
import jakarta.validation.constraints.Positive;
import lombok.Data;
//Never use final with fields inside Event Class since the event is can change based on the command.


import java.math.BigDecimal;
@Data
public class AccountUpdatedEvent {
    private Long accountNumber;
    private String mobileNumber;
    private  String branchAddress;
    private  AccountType accountType;
    private  boolean activeSw;
    @Positive
    private  BigDecimal balance;
    private  AccountStatus accountStatus;
    private  AccountCategory accountCategory;
}
