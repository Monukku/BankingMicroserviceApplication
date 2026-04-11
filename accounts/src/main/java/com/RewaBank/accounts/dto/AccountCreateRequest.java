package com.rewabank.accounts.dto;

import com.rewabank.accounts.entity.Account;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AccountCreateRequest(

        @NotNull(message = "Account type is required")
        Account.AccountType accountType,

        @Size(max = 10, message = "Branch code max 10 chars")
        String branchCode,

        @Size(max = 11, message = "IFSC code max 11 chars")
        String ifscCode
) {}
