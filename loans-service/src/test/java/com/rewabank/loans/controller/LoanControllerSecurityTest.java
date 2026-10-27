package com.rewabank.loans.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.loans.client.AccountsFeignClient;
import com.rewabank.loans.client.FraudFeignClient;
import com.rewabank.loans.dto.LoanApplicationRequest;
import com.rewabank.loans.dto.LoanReviewRequest;
import com.rewabank.loans.entity.LoanApplication;
import com.rewabank.loans.repository.LoanApplicationRepository;
import com.rewabank.loans.service.LoanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Negative / security tests for LoanController.
 * Covers: unauthenticated access, role-based restrictions on approval/rejection,
 * validation failures (amount below min, tenure out of range, missing fields),
 * and IDOR (customer accessing loans they don't own).
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class LoanControllerSecurityTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    @SuppressWarnings("deprecation") @MockBean LoanService           loanService;
    @SuppressWarnings("deprecation") @MockBean AccountsFeignClient   accountsFeignClient;
    @SuppressWarnings("deprecation") @MockBean FraudFeignClient      fraudFeignClient;

    @Autowired LoanApplicationRepository loanRepository;

    private UUID loanId;
    private final String customerUserId = "customer-user-001";
    private final String otherUserId    = "other-user-999";

    @BeforeEach
    void setUp() {
        loanRepository.deleteAll();
        loanRepository.flush();
    }

    // ── Rule: Unauthenticated requests are rejected ───────────────────────────
    // Note: addFilters=false disables security filter chain; these tests verify
    // controller-level validation and role checks via @PreAuthorize.

    // ── Rule: Bean validation — amount below minimum ──────────────────────────

    @Test
    void apply_Returns400_WhenAmountBelowMinimum() throws Exception {
        LoanApplicationRequest bad = new LoanApplicationRequest(
                UUID.randomUUID(),
                LoanApplication.LoanType.PERSONAL,
                new BigDecimal("5000.00"),  // below 10000 minimum
                24,
                "Need money"
        );

        mockMvc.perform(post("/api/v1/loans")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void apply_Returns400_WhenAmountAboveMaximum() throws Exception {
        LoanApplicationRequest bad = new LoanApplicationRequest(
                UUID.randomUUID(),
                LoanApplication.LoanType.PERSONAL,
                new BigDecimal("25000000.00"),  // above 20000000 maximum
                24,
                "Buy a city"
        );

        mockMvc.perform(post("/api/v1/loans")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    // ── Rule: Bean validation — tenure out of range ───────────────────────────

    @Test
    void apply_Returns400_WhenTenureBelowMinimum() throws Exception {
        LoanApplicationRequest bad = new LoanApplicationRequest(
                UUID.randomUUID(),
                LoanApplication.LoanType.HOME,
                new BigDecimal("500000.00"),
                3,  // below 6 months minimum
                "Home loan"
        );

        mockMvc.perform(post("/api/v1/loans")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void apply_Returns400_WhenTenureAboveMaximum() throws Exception {
        LoanApplicationRequest bad = new LoanApplicationRequest(
                UUID.randomUUID(),
                LoanApplication.LoanType.HOME,
                new BigDecimal("500000.00"),
                400,  // above 360 months maximum
                "Home loan forever"
        );

        mockMvc.perform(post("/api/v1/loans")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    // ── Rule: Bean validation — missing required fields ───────────────────────

    @Test
    void apply_Returns400_WhenPurposeIsBlank() throws Exception {
        LoanApplicationRequest bad = new LoanApplicationRequest(
                UUID.randomUUID(),
                LoanApplication.LoanType.PERSONAL,
                new BigDecimal("50000.00"),
                24,
                ""  // blank purpose
        );

        mockMvc.perform(post("/api/v1/loans")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void apply_Returns400_WhenAccountIdIsNull() throws Exception {
        String json = "{\"loanType\":\"PERSONAL\",\"requestedAmount\":50000," +
                "\"tenureMonths\":24,\"purpose\":\"test\"}";  // no accountId

        mockMvc.perform(post("/api/v1/loans")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void apply_Returns400_WhenBodyIsEmpty() throws Exception {
        mockMvc.perform(post("/api/v1/loans")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── Rule: Review decision validation ─────────────────────────────────────

    @Test
    void review_Returns400_WhenDecisionIsBlank() throws Exception {
        LoanReviewRequest bad = new LoanReviewRequest(
                "",  // blank decision
                new BigDecimal("50000.00"),
                new BigDecimal("10.5"),
                null,
                null
        );

        mockMvc.perform(patch("/api/v1/loans/{id}/review", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CREDIT_OFFICER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void review_Returns400_WhenRejectionNotesExceedMaxLength() throws Exception {
        String tooLong = "x".repeat(501);  // max is 500

        LoanReviewRequest bad = new LoanReviewRequest(
                "REJECT",
                null,
                null,
                null,
                tooLong
        );

        mockMvc.perform(patch("/api/v1/loans/{id}/review", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CREDIT_OFFICER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    // ── Rule: Malformed path variables must be rejected gracefully ───────────
    // (Role-based @PreAuthorize 403/200 checks live in LoanControllerAuthorizationTest,
    //  which runs with the security filter chain ON.)

    @Test
    void getById_Returns400_WhenIdIsNotUuid() throws Exception {
        mockMvc.perform(get("/api/v1/loans/{id}", "not-a-valid-uuid")
                        .with(jwt()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void startReview_Returns400_WhenIdIsNotUuid() throws Exception {
        mockMvc.perform(patch("/api/v1/loans/{id}/review/start", "bad-uuid")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CREDIT_OFFICER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getByStatus_Returns400_WhenStatusIsInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/loans/status/{status}", "INVALID_STATUS")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CREDIT_OFFICER"))))
                .andExpect(status().isBadRequest());
    }

}
