package com.rewabank.auth.dto;

import com.rewabank.auth.entity.OtpRecord;
import jakarta.validation.constraints.NotNull;

public record OtpRequest(

        @NotNull(message = "OTP purpose is required")
        OtpRecord.OtpPurpose purpose,

        // Optional — e.g. transactionId for TRANSFER, cardId for CARD_BLOCK
        String referenceId
) {}
