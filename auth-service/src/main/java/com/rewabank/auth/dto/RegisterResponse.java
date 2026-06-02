package com.rewabank.auth.dto;

import java.time.LocalDateTime;

public record RegisterResponse(
        String userId,
        String email,
        String maskedMobile,
        String message,
        LocalDateTime createdAt
) {}
