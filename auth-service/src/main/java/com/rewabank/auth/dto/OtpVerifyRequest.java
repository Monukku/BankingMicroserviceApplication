package com.rewabank.auth.dto;

import com.rewabank.auth.entity.OtpRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OtpVerifyRequest(

        @NotBlank(message = "OTP is required")
        @Size(min = 6, max = 6, message = "OTP must be 6 digits")
        String otp,

        @NotNull(message = "OTP purpose is required")
        OtpRecord.OtpPurpose purpose,

        String referenceId
) {}
