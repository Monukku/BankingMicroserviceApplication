package com.rewabank.accounts.dto;

// Response from Customers MS KYC gate call
// fullName/email/mobileNumber used to enrich Kafka events without a second Feign call
public record KycStatusResponse(
        String customerId,
        String keycloakUserId,
        String kycStatus,
        boolean kycVerified,
        String message,
        String fullName,
        String email,
        String mobileNumber
) {}
