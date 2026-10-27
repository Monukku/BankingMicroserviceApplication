package com.rewabank.loans.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.loans.client.AccountsFeignClient;
import com.rewabank.loans.client.FraudFeignClient;
import com.rewabank.loans.dto.LoanReviewRequest;
import com.rewabank.loans.service.LoanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Role-based @PreAuthorize tests for LoanController.
 *
 * These run with the security filter chain ON (no addFilters=false) so that
 * .with(jwt().authorities(...)) actually populates the SecurityContext that
 * method security reads — a @PreAuthorize denial then yields 403 (via the
 * GlobalExceptionHandler) and an allowed role yields 200. The sibling
 * LoanControllerSecurityTest uses addFilters=false to exercise pure bean
 * validation (400) without the auth layer; the two concerns need opposite
 * filter settings, hence the split.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoanControllerAuthorizationTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    @SuppressWarnings("deprecation") @MockBean LoanService         loanService;
    @SuppressWarnings("deprecation") @MockBean AccountsFeignClient accountsFeignClient;
    @SuppressWarnings("deprecation") @MockBean FraudFeignClient    fraudFeignClient;

    // ── CUSTOMER role is denied on staff operations (403) ────────────────────

    @Test
    void startReview_Returns403_WhenCustomerRole() throws Exception {
        mockMvc.perform(patch("/api/v1/loans/{id}/review/start", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void review_Returns403_WhenCustomerRole() throws Exception {
        LoanReviewRequest req = new LoanReviewRequest(
                "APPROVE", new BigDecimal("50000.00"),
                new BigDecimal("10.5"), null, null);

        mockMvc.perform(patch("/api/v1/loans/{id}/review", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void disburse_Returns403_WhenCustomerRole() throws Exception {
        mockMvc.perform(patch("/api/v1/loans/{id}/disburse", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllLoans_Returns403_WhenCustomerRole() throws Exception {
        mockMvc.perform(get("/api/v1/loans")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getByStatus_Returns403_WhenCustomerRole() throws Exception {
        mockMvc.perform(get("/api/v1/loans/status/{status}", "APPLIED")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    // ── CREDIT_OFFICER can access review endpoints (200) ─────────────────────

    @Test
    void getAllLoans_Returns200_WhenCreditOfficerRole() throws Exception {
        when(loanService.getAllLoans(any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/loans")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CREDIT_OFFICER"))))
                .andExpect(status().isOk());
    }
}