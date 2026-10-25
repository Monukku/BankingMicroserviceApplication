package com.rewabank.accounts.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record RecurringDepositRequest(

        @NotNull(message = "Source account ID is required")
        UUID sourceAccountId,

        @NotNull(message = "Monthly installment amount is required")
        @DecimalMin(value = "500.00", message = "Minimum RD installment is ₹500")
        @DecimalMax(value = "1000000.00", message = "Maximum RD installment is ₹10,00,000")
        BigDecimal installmentAmount,

        @NotNull(message = "Tenure is required")
        @Min(value = 6,   message = "Minimum tenure is 6 months")
        @Max(value = 120, message = "Maximum tenure is 120 months (10 years)")
        Integer tenureMonths,

        @Size(max = 10, message = "Branch code max 10 chars")
        String branchCode,

        @Size(max = 11, message = "IFSC code max 11 chars")
        String ifscCode
) {}
