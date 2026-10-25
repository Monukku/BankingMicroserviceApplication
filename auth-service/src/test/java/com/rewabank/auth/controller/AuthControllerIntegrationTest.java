package com.rewabank.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.auth.dto.*;
import com.rewabank.auth.entity.OtpRecord;
import com.rewabank.auth.kafka.AuthEventProducer;
import com.rewabank.auth.repository.BankingUserRepository;
import com.rewabank.auth.service.KeycloakUserService;
import com.rewabank.auth.service.OtpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired MockMvc       mockMvc;
    @Autowired ObjectMapper  objectMapper;

    @SuppressWarnings("deprecation") @MockBean KeycloakUserService  keycloakUserService;
    @SuppressWarnings("deprecation") @MockBean OtpService           otpService;
    @SuppressWarnings("deprecation") @MockBean BankingUserRepository bankingUserRepository;
    @SuppressWarnings("deprecation") @MockBean AuthEventProducer    authEventProducer;
    @SuppressWarnings("deprecation") @MockBean Keycloak             keycloak;

    private static final String USER_ID = "kc-auth-test-001";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String userId, String role) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", userId)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt,
                        List.of(new SimpleGrantedAuthority(role))));
    }

    private RegisterRequest validRegisterRequest() {
        return new RegisterRequest(
                "Test User",
                "testuser@rewabank.com",
                "+91-9876543210",
                "Test@1234"
        );
    }

    // ── POST /api/v1/auth/register ────────────────────────────────────────────

    @Test
    void register_Returns201_WhenNewUser() throws Exception {
        when(bankingUserRepository.existsByEmailAndDeletedAtIsNull(any())).thenReturn(false);
        when(bankingUserRepository.existsByMobileNumberAndDeletedAtIsNull(any())).thenReturn(false);
        when(keycloakUserService.createUser(any())).thenReturn(USER_ID);
        when(bankingUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.email").value("testuser@rewabank.com"));
    }

    @Test
    void register_Returns409_WhenEmailAlreadyExists() throws Exception {
        when(bankingUserRepository.existsByEmailAndDeletedAtIsNull(any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest())))
                .andExpect(status().isConflict());
    }

    @Test
    void register_Returns409_WhenMobileAlreadyExists() throws Exception {
        when(bankingUserRepository.existsByEmailAndDeletedAtIsNull(any())).thenReturn(false);
        when(bankingUserRepository.existsByMobileNumberAndDeletedAtIsNull(any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest())))
                .andExpect(status().isConflict());
    }

    @Test
    void register_Returns400_WhenEmailIsInvalid() throws Exception {
        RegisterRequest bad = new RegisterRequest(
                "Test User", "not-an-email", "+91-9876543210", "Test@1234");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_Returns400_WhenPasswordTooWeak() throws Exception {
        RegisterRequest bad = new RegisterRequest(
                "Test User", "test@rewabank.com", "+91-9876543210", "weak");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/v1/auth/otp/generate ───────────────────────────────────────

    @Test
    void generateOtp_Returns200_WhenAuthenticated() throws Exception {
        authenticateAs(USER_ID, "ROLE_CUSTOMER");

        OtpResponse otpResponse = new OtpResponse(
                true, "OTP sent to +91XXXXXXX210", "+91XXXXXXX210", 300);
        when(otpService.generateOtp(any(), any())).thenReturn(otpResponse);

        OtpRequest request = new OtpRequest(OtpRecord.OtpPurpose.TRANSFER, null);

        mockMvc.perform(post("/api/v1/auth/otp/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── POST /api/v1/auth/otp/verify ─────────────────────────────────────────

    @Test
    void verifyOtp_Returns200_WhenOtpIsCorrect() throws Exception {
        authenticateAs(USER_ID, "ROLE_CUSTOMER");
        when(otpService.verifyOtp(any(), any(), any())).thenReturn(true);

        OtpVerifyRequest request = new OtpVerifyRequest(
                "123456", OtpRecord.OtpPurpose.TRANSFER, null);

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void verifyOtp_Returns200_WithFalse_WhenOtpIsWrong() throws Exception {
        authenticateAs(USER_ID, "ROLE_CUSTOMER");
        when(otpService.verifyOtp(any(), any(), any())).thenReturn(false);

        OtpVerifyRequest request = new OtpVerifyRequest(
                "000000", OtpRecord.OtpPurpose.TRANSFER, null);

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── GET /api/v1/auth/staff — SUPER_ADMIN only ─────────────────────────────

    @Test
    void listStaff_Returns403_WhenNotSuperAdmin() throws Exception {
        authenticateAs(USER_ID, "ROLE_CUSTOMER");

        mockMvc.perform(get("/api/v1/auth/staff"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listStaff_Returns403_WhenBranchManager() throws Exception {
        authenticateAs(USER_ID, "ROLE_BRANCH_MANAGER");

        mockMvc.perform(get("/api/v1/auth/staff"))
                .andExpect(status().isForbidden());
    }
}
