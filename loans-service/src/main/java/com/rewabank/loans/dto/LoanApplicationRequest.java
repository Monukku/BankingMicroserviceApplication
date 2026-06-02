package com.rewabank.loans.dto;

import com.rewabank.loans.entity.LoanApplication;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record LoanApplicationRequest(

        @NotNull(message = "Account ID is required")
        UUID accountId,

        @NotNull(message = "Loan type is required")
        LoanApplication.LoanType loanType,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "10000.00", message = "Minimum loan amount is Rs 10,000")
        @DecimalMax(value = "5000000.00", message = "Maximum loan amount is Rs 50,00,000")
        BigDecimal requestedAmount,

        @NotNull(message = "Tenure is required")
        @Min(value = 6, message = "Minimum tenure is 6 months")
        @Max(value = 360, message = "Maximum tenure is 360 months")
        Integer tenureMonths,

        @NotBlank(message = "Purpose is required")
        @Size(max = 500)
        String purpose
) {}