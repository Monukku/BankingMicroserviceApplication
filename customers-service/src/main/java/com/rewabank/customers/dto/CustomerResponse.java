package com.rewabank.customers.dto;

import com.rewabank.customers.entity.Customer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String keycloakUserId,
        String email,
        String fullName,
        String maskedMobile,
        LocalDate dateOfBirth,
        Customer.KycStatus kycStatus,
        LocalDateTime kycSubmittedAt,
        LocalDateTime kycVerifiedAt,
        LocalDateTime createdAt
) {}
