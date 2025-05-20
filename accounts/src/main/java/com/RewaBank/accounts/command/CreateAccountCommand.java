package com.RewaBank.accounts.command;

import com.RewaBank.accounts.Utility.AccountCategory;
import com.RewaBank.accounts.Utility.AccountStatus;
import com.RewaBank.accounts.Utility.AccountType;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Data;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.math.BigDecimal;

@Builder
@Data
public class CreateAccountCommand {

    @TargetAggregateIdentifier
    private  Long accountNumber;
    private  String mobileNumber;
    private String branchAddress;
    private AccountType accountType;
    private boolean activeSw;
    @Positive
    private BigDecimal balance;
    private AccountStatus accountStatus;
    private AccountCategory accountCategory;

}
