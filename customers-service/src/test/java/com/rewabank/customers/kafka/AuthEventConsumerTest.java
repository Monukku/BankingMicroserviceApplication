package com.rewabank.customers.kafka;

import com.rewabank.customers.entity.Customer;
import com.rewabank.customers.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthEventConsumerTest {

    @Mock CustomerRepository customerRepository;
    @InjectMocks AuthEventConsumer authEventConsumer;

    private static Map<String, Object> event(String keycloakUserId) {
        return Map.of(
                "keycloakUserId", keycloakUserId,
                "email",          "test@rewabank.com",
                "mobileNumber",   "9876543210",
                "fullName",       "Test User"
        );
    }

    @Test
    void handleUserRegistered_newCustomer_createsProfileWithCorrectFields() {
        when(customerRepository.existsByKeycloakUserIdAndDeletedAtIsNull("kc-new-1"))
                .thenReturn(false);

        authEventConsumer.handleUserRegistered(event("kc-new-1"));

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        Customer saved = captor.getValue();
        assertThat(saved.getKeycloakUserId()).isEqualTo("kc-new-1");
        assertThat(saved.getEmail()).isEqualTo("test@rewabank.com");
        assertThat(saved.getMobileNumber()).isEqualTo("9876543210");
        assertThat(saved.getFullName()).isEqualTo("Test User");
        assertThat(saved.getKycStatus()).isEqualTo(Customer.KycStatus.NOT_SUBMITTED);
    }

    @Test
    void handleUserRegistered_existingCustomer_skipsCreate() {
        // Kills NegateConditionals on the idempotency guard
        when(customerRepository.existsByKeycloakUserIdAndDeletedAtIsNull("kc-existing-1"))
                .thenReturn(true);

        authEventConsumer.handleUserRegistered(event("kc-existing-1"));

        verify(customerRepository, never()).save(any());
    }
}