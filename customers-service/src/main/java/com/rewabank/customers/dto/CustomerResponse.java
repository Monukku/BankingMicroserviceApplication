package com.rewabank.customers.dto;

import com.rewabank.customers.entity.Customer;
import com.rewabank.customers.entity.KycDocument;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
        LocalDateTime createdAt,
        AddressSummary address,
        List<DocumentSummary> documents,
        String maskedAadhaar,
        String maskedPan,
        String rejectionReason
) {
    public record AddressSummary(
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String pincode,
            String country
    ) {}

    public record DocumentSummary(
            UUID id,
            KycDocument.DocumentType documentType,
            KycDocument.DocumentStatus status
    ) {}
}
