package com.rewabank.customers.services;

import com.rewabank.customers.dto.CustomerResponse;
import com.rewabank.customers.dto.KycStatusResponse;
import com.rewabank.customers.entity.Address;
import com.rewabank.customers.entity.Customer;
import com.rewabank.customers.exception.CustomerException;
import com.rewabank.customers.repository.AddressRepository;
import com.rewabank.customers.repository.CustomerRepository;
import com.rewabank.customers.repository.KycDocumentRepository;
import com.rewabank.customers.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rewabank.customers.entity.KycDocument;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private static final String ERR_CODE_NOT_FOUND = "CUST_001";
    private static final String ERR_CUSTOMER_NOT_FOUND = "Customer not found";
    private final CustomerRepository    customerRepository;
    private final AddressRepository     addressRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final EncryptionUtil        encryptionUtil;

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
                kycStatusMessage(customer.getKycStatus()),
                customer.getFullName(),
                customer.getEmail(),
                customer.getMobileNumber()
        );
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> searchByEmail(String email) {
        return customerRepository.findByEmailContainingIgnoreCaseAndDeletedAtIsNull(email)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> searchByName(String name) {
        String pattern = "%" + name.toLowerCase() + "%";
        return customerRepository.searchByName(pattern)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public CustomerResponse updateAddress(String keycloakUserId, Map<String, String> body) {
        Customer customer = customerRepository
                .findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId)
                .orElseThrow(() -> new CustomerException(ERR_CODE_NOT_FOUND, ERR_CUSTOMER_NOT_FOUND));

        Address address = customer.getAddress();
        if (address == null) {
            address = Address.builder().customer(customer).build();
        }
        if (body.containsKey("addressLine1")) address.setAddressLine1(body.get("addressLine1"));
        if (body.containsKey("addressLine2")) address.setAddressLine2(body.get("addressLine2"));
        if (body.containsKey("city"))         address.setCity(body.get("city"));
        if (body.containsKey("state"))        address.setState(body.get("state"));
        if (body.containsKey("pincode"))      address.setPincode(body.get("pincode"));
        if (body.containsKey("country"))      address.setCountry(body.get("country"));
        addressRepository.save(address);
        customer.setAddress(address);

        log.info("Address updated for customer {}", keycloakUserId);
        return toResponse(customer);
    }

    @CacheEvict(value = "kyc-status", key = "#customerId")
    public void evictKycCache(UUID customerId) {
        log.debug("KYC cache evicted for customer {}", customerId);
    }

    private CustomerResponse toResponse(Customer c) {
        CustomerResponse.AddressSummary addressSummary = null;
        if (c.getAddress() != null) {
            Address a = c.getAddress();
            addressSummary = new CustomerResponse.AddressSummary(
                    a.getAddressLine1(), a.getAddressLine2(),
                    a.getCity(), a.getState(), a.getPincode(), a.getCountry()
            );
        }

        // Deduplicate: latest upload per document type only
        List<CustomerResponse.DocumentSummary> documents = kycDocumentRepository
                .findByCustomerIdOrderByCreatedAtDesc(c.getId())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        KycDocument::getDocumentType,
                        d -> d,
                        (existing, replacement) -> existing)) // first = latest (already sorted desc)
                .values().stream()
                .sorted(Comparator.comparing(d -> d.getDocumentType().name()))
                .map(d -> new CustomerResponse.DocumentSummary(d.getId(), d.getDocumentType(), d.getStatus()))
                .toList();

        // Decrypt and mask Aadhaar/PAN — never return plaintext
        String maskedAadhaar = null;
        String maskedPan = null;
        try {
            if (c.getAadhaarNumberEncrypted() != null)
                maskedAadhaar = encryptionUtil.maskAadhaar(
                        encryptionUtil.decrypt(c.getAadhaarNumberEncrypted()));
            if (c.getPanNumberEncrypted() != null)
                maskedPan = encryptionUtil.maskPan(
                        encryptionUtil.decrypt(c.getPanNumberEncrypted()));
        } catch (Exception e) {
            log.warn("Could not mask Aadhaar/PAN for customer {}: {}", c.getId(), e.getMessage());
        }

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
                c.getCreatedAt(),
                addressSummary,
                documents,
                maskedAadhaar,
                maskedPan,
                c.getKycRejectionReason()
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
