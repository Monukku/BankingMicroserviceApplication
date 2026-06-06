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
public class UpdateAccountCommand {

    @TargetAggregateIdentifier
    private final Long accountNumber;
    private final String mobileNumber;
    private final String branchAddress;
    private final AccountType accountType;
    private  final boolean activeSw;
    @Positive
    private final BigDecimal balance;
    private final AccountStatus accountStatus;
    private final AccountCategory accountCategory;

}
