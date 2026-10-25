package com.rewabank.accounts.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record SalaryAccountRequest(

        @NotNull(message = "Customer ID is required")
        UUID customerId,

        @NotBlank(message = "Employer name is required")
        @Size(max = 200, message = "Employer name max 200 chars")
        String employerName,

        @NotBlank(message = "Employer code is required")
        @Size(max = 50, message = "Employer code max 50 chars")
        String employerCode,

        @Size(max = 10, message = "Branch code max 10 chars")
        String branchCode,

        @Size(max = 11, message = "IFSC code max 11 chars")
        String ifscCode
) {}
