package com.rewabank.customers.dto;

import com.rewabank.customers.entity.Customer;

// Used by Accounts MS KYC gate sync call
// fullName, email, mobileNumber included so Accounts MS can enrich Kafka events
// without a second Feign call
public record KycStatusResponse(
        String customerId,
        String keycloakUserId,
        Customer.KycStatus kycStatus,
        boolean kycVerified,
        String message,
        String fullName,
        String email,
        String mobileNumber
) {}
