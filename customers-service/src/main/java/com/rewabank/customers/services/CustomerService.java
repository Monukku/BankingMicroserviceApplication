package com.rewabank.customers.services;

import com.rewabank.customers.dto.CustomerResponse;
import com.rewabank.customers.dto.KycStatusResponse;
import com.rewabank.customers.entity.Customer;
import com.rewabank.customers.exception.CustomerException;
import com.rewabank.customers.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private static final String ERR_CODE_NOT_FOUND = "CUST_001";
    private static final String ERR_CUSTOMER_NOT_FOUND = "Customer not found";
    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public CustomerResponse getByKeycloakUserId(String keycloakUserId) {
        Customer customer = customerRepository
                .findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId)
                .orElseThrow(() -> new CustomerException(ERR_CODE_NOT_FOUND, ERR_CUSTOMER_NOT_FOUND));
        return toResponse(customer);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getById(UUID id) {
        Customer customer = customerRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomerException(ERR_CODE_NOT_FOUND, ERR_CUSTOMER_NOT_FOUND));
        return toResponse(customer);
    }

    // KYC gate — called by Accounts MS sync (cached 10 min in Redis)
    @Cacheable(value = "kyc-status", key = "#customerId")
    @Transactional(readOnly = true)
    public KycStatusResponse getKycStatus(UUID customerId) {
        Customer customer = customerRepository
                .findByIdAndDeletedAtIsNull(customerId)
                .orElseThrow(() -> new CustomerException(ERR_CODE_NOT_FOUND, ERR_CUSTOMER_NOT_FOUND));

        return new KycStatusResponse(
                customer.getId().toString(),
                customer.getKeycloakUserId(),
                customer.getKycStatus(),
                customer.isKycVerified(),
                kycStatusMessage(customer.getKycStatus())
        );
    }

    @CacheEvict(value = "kyc-status", key = "#customerId")
    public void evictKycCache(UUID customerId) {
        log.debug("KYC cache evicted for customer {}", customerId);
    }

    private CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(
                c.getId(),
                c.getKeycloakUserId(),
                c.getEmail(),
                c.getFullName(),
                maskMobile(c.getMobileNumber()),
                c.getDateOfBirth(),
                c.getKycStatus(),
                c.getKycSubmittedAt(),
                c.getKycVerifiedAt(),
                c.getCreatedAt()
        );
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 10) return "XXXXXXXXXX";
        return mobile.substring(0, 3) + "XXXXXXX" + mobile.substring(mobile.length() - 3);
    }

    private String kycStatusMessage(Customer.KycStatus status) {
        return switch (status) {
            case NOT_SUBMITTED -> "KYC documents not submitted yet";
            case SUBMITTED     -> "KYC documents submitted, pending review";
            case UNDER_REVIEW  -> "KYC is under review by our team";
            case VERIFIED      -> "KYC verified — account can be activated";
            case REJECTED      -> "KYC rejected — please resubmit documents";
        };
    }
}
