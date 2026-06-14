package com.rewabank.customers.services;

import com.rewabank.customers.dto.CustomerResponse;
import com.rewabank.customers.dto.KycStatusResponse;
import com.rewabank.customers.entity.Customer;
import com.rewabank.customers.exception.CustomerException;
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
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock CustomerRepository customerRepository;
    @Mock EncryptionUtil encryptionUtil;

    @InjectMocks
    CustomerService customerService;

    private UUID customerId;
    private Customer customer;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        customer = Customer.builder()
                .id(customerId)
                .keycloakUserId("kc-user-1")
                .email("rewa@rewabank.com")
                .fullName("Rewa Test")
                .mobileNumber("9876543210")
                .dateOfBirth(LocalDate.of(1990, 6, 15))
                .kycStatus(Customer.KycStatus.NOT_SUBMITTED)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // --- getByKeycloakUserId ---

    @Test
    void getByKeycloakUserId_found_returnsResponse() {
        when(customerRepository.findByKeycloakUserIdAndDeletedAtIsNull("kc-user-1"))
                .thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.getByKeycloakUserId("kc-user-1");

        assertThat(response.keycloakUserId()).isEqualTo("kc-user-1");
        assertThat(response.email()).isEqualTo("rewa@rewabank.com");
        assertThat(response.id()).isEqualTo(customerId);
    }

    @Test
    void getByKeycloakUserId_notFound_throwsCustomerException() {
        when(customerRepository.findByKeycloakUserIdAndDeletedAtIsNull("unknown"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getByKeycloakUserId("unknown"))
                .isInstanceOf(CustomerException.class)
                .hasMessageContaining("Customer not found");
    }

    // --- getById ---

    @Test
    void getById_found_returnsResponse() {
        when(customerRepository.findByIdAndDeletedAtIsNull(customerId))
                .thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.getById(customerId);

        assertThat(response.id()).isEqualTo(customerId);
        assertThat(response.fullName()).isEqualTo("Rewa Test");
    }

    @Test
    void getById_notFound_throwsCustomerException() {
        UUID unknownId = UUID.randomUUID();
        when(customerRepository.findByIdAndDeletedAtIsNull(unknownId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getById(unknownId))
                .isInstanceOf(CustomerException.class)
                .hasMessageContaining("Customer not found");
    }

    // --- getKycStatus ---

    @Test
    void getKycStatus_notSubmitted_returnsFalseAndMessage() {
        when(customerRepository.findByIdAndDeletedAtIsNull(customerId))
                .thenReturn(Optional.of(customer));

        KycStatusResponse response = customerService.getKycStatus(customerId);

        assertThat(response.kycStatus()).isEqualTo(Customer.KycStatus.NOT_SUBMITTED);
        assertThat(response.kycVerified()).isFalse();
        assertThat(response.message()).contains("not submitted");
    }

    @Test
    void getKycStatus_verified_returnsTrueAndMessage() {
        customer.setKycStatus(Customer.KycStatus.VERIFIED);
        when(customerRepository.findByIdAndDeletedAtIsNull(customerId))
                .thenReturn(Optional.of(customer));

        KycStatusResponse response = customerService.getKycStatus(customerId);

        assertThat(response.kycVerified()).isTrue();
        assertThat(response.message()).contains("account can be activated");
    }

    @Test
    void getKycStatus_submitted_pendingMessage() {
        customer.setKycStatus(Customer.KycStatus.SUBMITTED);
        when(customerRepository.findByIdAndDeletedAtIsNull(customerId))
                .thenReturn(Optional.of(customer));

        KycStatusResponse response = customerService.getKycStatus(customerId);

        assertThat(response.kycVerified()).isFalse();
        assertThat(response.message()).contains("pending review");
    }

    @Test
    void getKycStatus_rejected_rejectedMessage() {
        customer.setKycStatus(Customer.KycStatus.REJECTED);
        when(customerRepository.findByIdAndDeletedAtIsNull(customerId))
                .thenReturn(Optional.of(customer));

        KycStatusResponse response = customerService.getKycStatus(customerId);

        assertThat(response.kycVerified()).isFalse();
        assertThat(response.message()).contains("resubmit");
    }

    @Test
    void getKycStatus_notFound_throwsCustomerException() {
        UUID unknownId = UUID.randomUUID();
        when(customerRepository.findByIdAndDeletedAtIsNull(unknownId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getKycStatus(unknownId))
                .isInstanceOf(CustomerException.class)
                .hasMessageContaining("Customer not found");
    }

    @Test
    void getKycStatus_allStatuses_haveNonBlankMessage() {
        for (Customer.KycStatus status : Customer.KycStatus.values()) {
            customer.setKycStatus(status);
            when(customerRepository.findByIdAndDeletedAtIsNull(customerId))
                    .thenReturn(Optional.of(customer));

            KycStatusResponse response = customerService.getKycStatus(customerId);

            assertThat(response.message())
                    .as("message for status %s must not be blank", status)
                    .isNotBlank();
        }
    }

    // --- mobile masking ---

    @Test
    void maskedMobile_tenDigitNumber_masksMiddle() {
        when(customerRepository.findByIdAndDeletedAtIsNull(customerId))
                .thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.getById(customerId);

        // 9876543210 → 987XXXXXXX210
        assertThat(response.maskedMobile()).startsWith("987");
        assertThat(response.maskedMobile()).endsWith("210");
        assertThat(response.maskedMobile()).contains("XXXXXXX");
        assertThat(response.maskedMobile()).doesNotContain("6543");
    }

    @Test
    void maskedMobile_shortNumber_returnsPlaceholder() {
        customer.setMobileNumber("123");
        when(customerRepository.findByIdAndDeletedAtIsNull(customerId))
                .thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.getById(customerId);

        assertThat(response.maskedMobile()).isEqualTo("XXXXXXXXXX");
    }

    @Test
    void maskedMobile_nullNumber_returnsPlaceholder() {
        customer.setMobileNumber(null);
        when(customerRepository.findByIdAndDeletedAtIsNull(customerId))
                .thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.getById(customerId);

        assertThat(response.maskedMobile()).isEqualTo("XXXXXXXXXX");
    }

    // --- evictKycCache ---

    @Test
    void evictKycCache_doesNotThrow() {
        assertThatCode(() -> customerService.evictKycCache(customerId))
                .doesNotThrowAnyException();
    }
}