package com.rewabank.loans.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record LoanReviewRequest(

        @NotBlank(message = "Decision is required")
        String decision,           // APPROVE or REJECT

        BigDecimal approvedAmount, // required if APPROVE

        BigDecimal interestRate,   // required if APPROVE

        @Size(max = 500)
        String reviewNotes,

        @Size(max = 500)
        String rejectionReason     // required if REJECT
) {}