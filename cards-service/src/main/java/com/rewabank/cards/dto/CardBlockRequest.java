package com.rewabank.cards.dto;

import jakarta.validation.constraints.Size;

public record CardBlockRequest(
        // OTP required for customer self-block (RBI mandate); null for staff block
        @Size(min = 6, max = 6, message = "OTP must be 6 digits")
        String otp,

        @Size(max = 500)
        String reason
) {}