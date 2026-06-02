package com.rewabank.auth.dto;

public record OtpResponse(
        boolean success,
        String message,
        String maskedMobile,  // +91XXXXXXX890
        Integer expiresInSeconds
) {}
