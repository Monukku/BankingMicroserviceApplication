package com.rewabank.customers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KycVerifyRequest(
        @NotBlank(message = "Decision is required")
        String decision,    // VERIFIED or REJECTED

        @Size(max = 500)
        String rejectionReason  // required if REJECTED
) {}
