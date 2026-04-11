package com.rewabank.accounts.dto;

// Response from Customers MS KYC gate call
public record KycStatusResponse(
        String customerId,
        String keycloakUserId,
        String kycStatus,
        boolean kycVerified,
        String message
) {}
