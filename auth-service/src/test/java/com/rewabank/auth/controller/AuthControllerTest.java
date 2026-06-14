package com.rewabank.auth.controller;

import com.rewabank.auth.dto.RegisterRequest;
import com.rewabank.auth.entity.BankingUser;
import com.rewabank.auth.exception.AuthException;
import com.rewabank.auth.kafka.AuthEventProducer;
import com.rewabank.auth.repository.BankingUserRepository;
import com.rewabank.auth.service.KeycloakUserService;
import com.rewabank.auth.service.OtpService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock KeycloakUserService   keycloakUserService;
    @Mock OtpService            otpService;
    @Mock BankingUserRepository bankingUserRepository;
    @Mock AuthEventProducer     authEventProducer;
    @InjectMocks AuthController controller;

    private BankingUser stubUser(String mobile) {
        BankingUser user = mock(BankingUser.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(user.getEmail()).thenReturn("test@rewabank.com");
        when(user.getFullName()).thenReturn("Test User");
        when(user.getMobileNumber()).thenReturn(mobile);
        when(user.getKycVerified()).thenReturn(false);
        when(user.getStatus()).thenReturn(BankingUser.UserStatus.ACTIVE);
        return user;
    }

    private Jwt mockJwt(String subject) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(subject);
        return jwt;
    }

    // ── getCurrentUser / maskMobile ───────────────────────────────────────────

    @Test
    void getCurrentUser_tenDigitMobile_masksCorrectly() {
        // Kills MathMutator on: length - 3, NullReturn on method return
        // stubUser() must be called BEFORE when() to avoid nested Mockito stubbing
        BankingUser user = stubUser("9876543210");
        when(bankingUserRepository.findByKeycloakUserIdAndDeletedAtIsNull("kc-1"))
                .thenReturn(Optional.of(user));

        ResponseEntity<?> response = controller.getCurrentUser(mockJwt("kc-1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("maskedMobile").toString())
                .startsWith("987").endsWith("210").contains("XXXXXXX");
    }

    @Test
    void getCurrentUser_nullMobile_returnsPlaceholder() {
        // Kills NegateConditionals on: mobile == null || mobile.length() < 10
        BankingUser user = stubUser(null);
        when(bankingUserRepository.findByKeycloakUserIdAndDeletedAtIsNull("kc-1"))
                .thenReturn(Optional.of(user));

        ResponseEntity<?> response = controller.getCurrentUser(mockJwt("kc-1"));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("maskedMobile")).isEqualTo("XXXXXXXXXX");
    }

    @Test
    void getCurrentUser_shortMobile_returnsPlaceholder() {
        // Kills NegateConditionals on: length < 10
        BankingUser user = stubUser("123456789"); // 9 chars
        when(bankingUserRepository.findByKeycloakUserIdAndDeletedAtIsNull("kc-1"))
                .thenReturn(Optional.of(user));

        ResponseEntity<?> response = controller.getCurrentUser(mockJwt("kc-1"));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("maskedMobile")).isEqualTo("XXXXXXXXXX");
    }

    @Test
    void getCurrentUser_exactlyTenDigits_masksInsteadOfPlaceholder() {
        // Kills ConditionalsBoundary: length < 10 vs length <= 10
        BankingUser user = stubUser("9876543210"); // exactly 10
        when(bankingUserRepository.findByKeycloakUserIdAndDeletedAtIsNull("kc-1"))
                .thenReturn(Optional.of(user));

        ResponseEntity<?> response = controller.getCurrentUser(mockJwt("kc-1"));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("maskedMobile").toString()).isNotEqualTo("XXXXXXXXXX");
    }

    @Test
    void getCurrentUser_notFound_throwsAuthException() {
        when(bankingUserRepository.findByKeycloakUserIdAndDeletedAtIsNull("unknown"))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> controller.getCurrentUser(mockJwt("unknown")))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("User not found");
    }

    // ── register — duplicate checks ───────────────────────────────────────────

    @Test
    void register_emailAlreadyExists_throwsAuthException() {
        // Kills NegateConditionals on: existsByEmail...
        RegisterRequest req = new RegisterRequest(
                "Test User", "dup@rewabank.com", "+91-9876543210", "Abc@1234!");
        when(bankingUserRepository.existsByEmailAndDeletedAtIsNull("dup@rewabank.com"))
                .thenReturn(true);

        assertThatThrownBy(() -> controller.register(req))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void register_mobileAlreadyExists_throwsAuthException() {
        // Kills NegateConditionals on: existsByMobileNumber...
        RegisterRequest req = new RegisterRequest(
                "Test User", "new@rewabank.com", "+91-9876543210", "Abc@1234!");
        when(bankingUserRepository.existsByEmailAndDeletedAtIsNull("new@rewabank.com"))
                .thenReturn(false);
        when(bankingUserRepository.existsByMobileNumberAndDeletedAtIsNull("+91-9876543210"))
                .thenReturn(true);

        assertThatThrownBy(() -> controller.register(req))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("already exists");
    }
}