package com.rewabank.customers.services;

import com.rewabank.customers.dto.AddressRequest;
import com.rewabank.customers.dto.KycSubmitRequest;
import com.rewabank.customers.dto.KycVerifyRequest;
import com.rewabank.customers.entity.Address;
import com.rewabank.customers.entity.Customer;
import com.rewabank.customers.exception.CustomerException;
import com.rewabank.customers.kafka.CustomerEventProducer;
import com.rewabank.customers.repository.AddressRepository;
import com.rewabank.customers.repository.CustomerRepository;
import com.rewabank.customers.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.rewabank.customers.services.CustomerService;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KycService {

    private final CustomerRepository  customerRepository;
    private final AddressRepository   addressRepository;
    private final CustomerService     customerService;
    private final CustomerEventProducer eventProducer;
    private final EncryptionUtil      encryptionUtil;

    // Customer submits KYC details + documents
    @Transactional
    public void submitKyc(String keycloakUserId, KycSubmitRequest request) {
        Customer customer = customerRepository
                .findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId)
                .orElseThrow(() -> new CustomerException("CUST_001", "Customer not found"));

        // Only allow submission from NOT_SUBMITTED or REJECTED state
        if (customer.getKycStatus() == Customer.KycStatus.VERIFIED) {
            throw new CustomerException("CUST_002", "KYC already verified");
        }
        if (customer.getKycStatus() == Customer.KycStatus.UNDER_REVIEW ||
                customer.getKycStatus() == Customer.KycStatus.SUBMITTED) {
            throw new CustomerException("CUST_003", "KYC already submitted and under review");
        }

        // Encrypt sensitive fields before saving
        customer.setAadhaarNumberEncrypted(encryptionUtil.encrypt(request.aadhaarNumber()));
        customer.setPanNumberEncrypted(encryptionUtil.encrypt(request.panNumber()));
        customer.setDateOfBirth(request.dateOfBirth());
        customer.setKycStatus(Customer.KycStatus.SUBMITTED);
        customer.setKycSubmittedAt(LocalDateTime.now());
        customer.setKycRejectionReason(null);  // clear any previous rejection reason
        customerRepository.save(customer);

        // Save address
        saveOrUpdateAddress(customer, request.address());

        // Evict cached KYC status
        customerService.evictKycCache(customer.getId());

        log.info("KYC submitted for customer {}", customer.getId());
        eventProducer.publishCustomerUpdated(customer.getId().toString(), "KYC_SUBMITTED");
    }

    // RM or Branch Manager verifies/rejects KYC
    @Transactional
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER', 'BRANCH_MANAGER', 'SUPER_ADMIN')")
    public void verifyKyc(UUID customerId, String verifierKeycloakId, KycVerifyRequest request) {
        Customer customer = customerRepository
                .findByIdAndDeletedAtIsNull(customerId)
                .orElseThrow(() -> new CustomerException("CUST_001", "Customer not found"));

        if (customer.getKycStatus() != Customer.KycStatus.SUBMITTED &&
                customer.getKycStatus() != Customer.KycStatus.UNDER_REVIEW) {
            throw new CustomerException("CUST_004",
                    "KYC cannot be verified — current status: " + customer.getKycStatus());
        }

        if ("VERIFIED".equalsIgnoreCase(request.decision())) {
            customer.setKycStatus(Customer.KycStatus.VERIFIED);
            customer.setKycVerifiedAt(LocalDateTime.now());
            customer.setKycVerifiedBy(verifierKeycloakId);
            customer.setKycRejectionReason(null);
            customerRepository.save(customer);

            // Evict cache so Accounts MS gets fresh status
            customerService.evictKycCache(customerId);

            // Async — publish to Kafka — Accounts MS will auto-activate account
            eventProducer.publishKycVerified(
                    customer.getId().toString(),
                    customer.getKeycloakUserId(),
                    verifierKeycloakId
            );

            log.info("KYC verified for customer {} by {}", customerId, verifierKeycloakId);

        } else if ("REJECTED".equalsIgnoreCase(request.decision())) {
            if (request.rejectionReason() == null || request.rejectionReason().isBlank()) {
                throw new CustomerException("CUST_005", "Rejection reason is required");
            }
            customer.setKycStatus(Customer.KycStatus.REJECTED);
            customer.setKycRejectionReason(request.rejectionReason());
            customerRepository.save(customer);

            customerService.evictKycCache(customerId);
            eventProducer.publishCustomerUpdated(customer.getId().toString(), "KYC_REJECTED");

            log.info("KYC rejected for customer {} reason: {}", customerId, request.rejectionReason());
        } else {
            throw new CustomerException("CUST_006", "Invalid decision. Use VERIFIED or REJECTED");
        }
    }

    // Move to UNDER_REVIEW when staff picks it up
    @Transactional
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER', 'BRANCH_MANAGER', 'SUPER_ADMIN')")
    public void startReview(UUID customerId) {
        Customer customer = customerRepository
                .findByIdAndDeletedAtIsNull(customerId)
                .orElseThrow(() -> new CustomerException("CUST_001", "Customer not found"));

        if (customer.getKycStatus() != Customer.KycStatus.SUBMITTED) {
            throw new CustomerException("CUST_004", "Customer KYC not in SUBMITTED state");
        }

        customer.setKycStatus(Customer.KycStatus.UNDER_REVIEW);
        customerRepository.save(customer);
        log.info("KYC moved to UNDER_REVIEW for customer {}", customerId);
    }

    private void saveOrUpdateAddress(Customer customer, AddressRequest req) {
        Address address = addressRepository.findByCustomerId(customer.getId())
                .stream().findFirst()
                .orElse(Address.builder().customer(customer).build());

        address.setAddressLine1(req.addressLine1());
        address.setAddressLine2(req.addressLine2());
        address.setCity(req.city());
        address.setState(req.state());
        address.setPincode(req.pincode());
        addressRepository.save(address);
    }
}
