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
import com.rewabank.customers.services.CustomerService;
import com.rewabank.customers.util.EncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KycServiceTest {

    @Mock CustomerRepository customerRepository;
    @Mock AddressRepository  addressRepository;
    @Mock CustomerService    customerService;
    @Mock CustomerEventProducer eventProducer;
    @Mock EncryptionUtil     encryptionUtil;

    @InjectMocks KycService kycService;

    private UUID customerId;
    private Customer customer;

    private static final AddressRequest ADDRESS = new AddressRequest(
            "12 MG Road", "Near Mall", "Rewa", "Madhya Pradesh", "486001");

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        customer = Customer.builder()
                .id(customerId)
                .keycloakUserId("kc-user-1")
                .email("test@rewabank.com")
                .fullName("Rewa Test")
                .mobileNumber("9876543210")
                .kycStatus(Customer.KycStatus.NOT_SUBMITTED)
                .build();
    }

    private KycSubmitRequest submitRequest() {
        return new KycSubmitRequest(
                "234567890123",
                "ABCDE1234F",
                LocalDate.of(1990, 5, 10),
                ADDRESS
        );
    }

    // ── submitKyc ────────────────────────────────────────────────

    @Test
    void submitKyc_notSubmitted_savesAndPublishesEvent() {
        when(customerRepository.findByKeycloakUserIdAndDeletedAtIsNull("kc-user-1"))
                .thenReturn(Optional.of(customer));
        when(encryptionUtil.encrypt(anyString())).thenReturn("encrypted");
        when(addressRepository.findByCustomerId(customerId)).thenReturn(List.of());
        when(addressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        kycService.submitKyc("kc-user-1", submitRequest());

        assertThat(customer.getKycStatus()).isEqualTo(Customer.KycStatus.SUBMITTED);
        verify(customerRepository).save(customer);
        verify(encryptionUtil, times(2)).encrypt(anyString()); // aadhaar + pan
        verify(customerService).evictKycCache(customerId);
        verify(eventProducer).publishCustomerUpdated(customerId.toString(), "KYC_SUBMITTED");
    }

    @Test
    void submitKyc_rejected_allowsResubmission() {
        customer.setKycStatus(Customer.KycStatus.REJECTED);
        when(customerRepository.findByKeycloakUserIdAndDeletedAtIsNull("kc-user-1"))
                .thenReturn(Optional.of(customer));
        when(encryptionUtil.encrypt(anyString())).thenReturn("encrypted");
        when(addressRepository.findByCustomerId(customerId)).thenReturn(List.of());
        when(addressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> kycService.submitKyc("kc-user-1", submitRequest()))
                .doesNotThrowAnyException();
        assertThat(customer.getKycStatus()).isEqualTo(Customer.KycStatus.SUBMITTED);
        assertThat(customer.getKycRejectionReason()).isNull();
    }

    @Test
    void submitKyc_alreadyVerified_throws() {
        customer.setKycStatus(Customer.KycStatus.VERIFIED);
        when(customerRepository.findByKeycloakUserIdAndDeletedAtIsNull("kc-user-1"))
                .thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> kycService.submitKyc("kc-user-1", submitRequest()))
                .isInstanceOf(CustomerException.class)
                .hasMessageContaining("already verified");
    }

    @Test
    void submitKyc_alreadySubmitted_throws() {
        customer.setKycStatus(Customer.KycStatus.SUBMITTED);
        when(customerRepository.findByKeycloakUserIdAndDeletedAtIsNull("kc-user-1"))
                .thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> kycService.submitKyc("kc-user-1", submitRequest()))
                .isInstanceOf(CustomerException.class)
                .hasMessageContaining("under review");
    }

    @Test
    void submitKyc_underReview_throws() {
        customer.setKycStatus(Customer.KycStatus.UNDER_REVIEW);
        when(customerRepository.findByKeycloakUserIdAndDeletedAtIsNull("kc-user-1"))
                .thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> kycService.submitKyc("kc-user-1", submitRequest()))
                .isInstanceOf(CustomerException.class)
                .hasMessageContaining("under review");
    }

    @Test
    void submitKyc_customerNotFound_throws() {
        when(customerRepository.findByKeycloakUserIdAndDeletedAtIsNull("ghost"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> kycService.submitKyc("ghost", submitRequest()))
                .isInstanceOf(CustomerException.class)
                .hasMessageContaining("Customer not found");
    }

    @Test
    void submitKyc_existingAddress_updatesInPlace() {
        Address existing = Address.builder().customer(customer).city("OldCity").build();
        when(customerRepository.findByKeycloakUserIdAndDeletedAtIsNull("kc-user-1"))
                .thenReturn(Optional.of(customer));
        when(encryptionUtil.encrypt(anyString())).thenReturn("encrypted");
        when(addressRepository.findByCustomerId(customerId)).thenReturn(List.of(existing));
        when(addressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        kycService.submitKyc("kc-user-1", submitRequest());

        // verify the same address object was updated, not a new one
        verify(addressRepository).save(existing);
        assertThat(existing.getCity()).isEqualTo("Rewa");
    }

    // ── verifyKyc ────────────────────────────────────────────────

    @Test
    void verifyKyc_approved_setsVerifiedAndPublishesEvent() {
        customer.setKycStatus(Customer.KycStatus.SUBMITTED);
        when(customerRepository.findByIdAndDeletedAtIsNull(customerId))
                .thenReturn(Optional.of(customer));

        kycService.verifyKyc(customerId, "rm-kc-123", new KycVerifyRequest("VERIFIED", null));

        assertThat(customer.getKycStatus()).isEqualTo(Customer.KycStatus.VERIFIED);
        assertThat(customer.getKycVerifiedBy()).isEqualTo("rm-kc-123");
        assertThat(customer.getKycVerifiedAt()).isNotNull();
        verify(customerService).evictKycCache(customerId);
        verify(eventProducer).publishKycVerified(customerId.toString(), customer.getKeycloakUserId(), "rm-kc-123");
    }

    @Test
    void verifyKyc_underReview_canBeApproved() {
        customer.setKycStatus(Customer.KycStatus.UNDER_REVIEW);
        when(customerRepository.findByIdAndDeletedAtIsNull(customerId))
                .thenReturn(Optional.of(customer));

        kycService.verifyKyc(customerId, "rm-kc-123", new KycVerifyRequest("VERIFIED", null));

        assertThat(customer.getKycStatus()).isEqualTo(Customer.KycStatus.VERIFIED);
    }

    @Test
    void verifyKyc_rejected_setsRejectedAndPublishesEvent() {
        customer.setKycStatus(Customer.KycStatus.SUBMITTED);
        when(customerRepository.findByIdAndDeletedAtIsNull(customerId))
                .thenReturn(Optional.of(customer));

        kycService.verifyKyc(customerId, "rm-kc-123", new KycVerifyRequest("REJECTED", "Photo mismatch"));

        assertThat(customer.getKycStatus()).isEqualTo(Customer.KycStatus.REJECTED);
        assertThat(customer.getKycRejectionReason()).isEqualTo("Photo mismatch");
        verify(customerService).evictKycCache(customerId);
        verify(eventProducer).publishCustomerUpdated(customerId.toString(), "KYC_REJECTED");
    }

    @Test
    void verifyKyc_rejectedWithoutReason_throws() {
        customer.setKycStatus(Customer.KycStatus.SUBMITTED);
        when(customerRepository.findByIdAndDeletedAtIsNull(customerId))
                .thenReturn(Optional.of(customer));

        assertThatThrownBy(() ->
                kycService.verifyKyc(customerId, "rm-kc-123", new KycVerifyRequest("REJECTED", null)))
                .isInstanceOf(CustomerException.class)
                .hasMessageContaining("Rejection reason is required");
    }

    @Test
    void verifyKyc_rejectedWithBlankReason_throws() {
        customer.setKycStatus(Customer.KycStatus.SUBMITTED);
        when(customerRepository.findByIdAndDeletedAtIsNull(customerId))
                .thenReturn(Optional.of(customer));

        assertThatThrownBy(() ->
                kycService.verifyKyc(customerId, "rm-kc-123", new KycVerifyRequest("REJECTED", "   ")))
                .isInstanceOf(CustomerException.class)
                .hasMessageContaining("Rejection reason is required");
    }

    @Test
    void verifyKyc_wrongStatus_notSubmitted_throws() {
        customer.setKycStatus(Customer.KycStatus.NOT_SUBMITTED);
        when(customerRepository.findByIdAndDeletedAtIsNull(customerId))
                .thenReturn(Optional.of(customer));

        assertThatThrownBy(() ->
                kycService.verifyKyc(customerId, "rm-kc-123", new KycVerifyRequest("VERIFIED", null)))
                .isInstanceOf(CustomerException.class)
                .hasMessageContaining("cannot be verified");
    }

    @Test
    void verifyKyc_invalidDecision_throws() {
        customer.setKycStatus(Customer.KycStatus.SUBMITTED);
        when(customerRepository.findByIdAndDeletedAtIsNull(customerId))
                .thenReturn(Optional.of(customer));

        assertThatThrownBy(() ->
                kycService.verifyKyc(customerId, "rm-kc-123", new KycVerifyRequest("APPROVE", null)))
                .isInstanceOf(CustomerException.class)
                .hasMessageContaining("Invalid decision");
    }

    @Test
    void verifyKyc_customerNotFound_throws() {
        UUID unknown = UUID.randomUUID();
        when(customerRepository.findByIdAndDeletedAtIsNull(unknown))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                kycService.verifyKyc(unknown, "rm-kc-123", new KycVerifyRequest("VERIFIED", null)))
                .isInstanceOf(CustomerException.class)
                .hasMessageContaining("Customer not found");
    }

    // ── startReview ──────────────────────────────────────────────

    @Test
    void startReview_submitted_movesToUnderReview() {
        customer.setKycStatus(Customer.KycStatus.SUBMITTED);
        when(customerRepository.findByIdAndDeletedAtIsNull(customerId))
                .thenReturn(Optional.of(customer));

        kycService.startReview(customerId);

        assertThat(customer.getKycStatus()).isEqualTo(Customer.KycStatus.UNDER_REVIEW);
        verify(customerRepository).save(customer);
    }

    @Test
    void startReview_notSubmitted_throws() {
        customer.setKycStatus(Customer.KycStatus.NOT_SUBMITTED);
        when(customerRepository.findByIdAndDeletedAtIsNull(customerId))
                .thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> kycService.startReview(customerId))
                .isInstanceOf(CustomerException.class)
                .hasMessageContaining("not in SUBMITTED state");
    }

    @Test
    void startReview_customerNotFound_throws() {
        UUID unknown = UUID.randomUUID();
        when(customerRepository.findByIdAndDeletedAtIsNull(unknown))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> kycService.startReview(unknown))
                .isInstanceOf(CustomerException.class)
                .hasMessageContaining("Customer not found");
    }
}