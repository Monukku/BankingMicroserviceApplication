package com.rewabank.customers.dto;

import com.rewabank.customers.entity.Customer;

// Used by Accounts MS KYC gate sync call
public record KycStatusResponse(
        String customerId,
        String keycloakUserId,
        Customer.KycStatus kycStatus,
        boolean kycVerified,
        String message
) {}
