package com.rewabank.accounts.dto;

import com.rewabank.accounts.entity.Account;
import java.util.UUID;

public record AccountLookupResponse(
        UUID   id,
        String accountNumber,
        String ifscCode,
        Account.AccountStatus status
) {}
