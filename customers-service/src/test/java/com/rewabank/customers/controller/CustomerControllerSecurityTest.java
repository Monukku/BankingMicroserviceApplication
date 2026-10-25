package com.rewabank.customers.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.customers.services.CustomerService;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Negative / security tests for CustomerController.
 * Covers: unauthenticated access, IDOR (customer accessing another customer's
 * profile), role-based access control on staff endpoints, and malformed input.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerControllerSecurityTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    @SuppressWarnings("deprecation") @MockBean CustomerService customerService;
    @SuppressWarnings("deprecation") @MockBean S3Client        s3Client;
    @SuppressWarnings("deprecation") @MockBean S3Presigner     s3Presigner;

    private static final UUID OTHER_CUSTOMER_ID = UUID.randomUUID();

    // ── Rule: Unauthenticated requests must be rejected ──────────────────────

    @Test
    void getMyProfile_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/customers/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateMyAddress_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(patch("/api/v1/customers/me")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"city\":\"Mumbai\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getById_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{id}", OTHER_CUSTOMER_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void search_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/customers/search")
                        .param("email", "test@test.com"))
                .andExpect(status().isUnauthorized());
    }

    // ── Rule: Staff-only endpoints must reject CUSTOMER role ─────────────────

    @Test
    void getById_Returns403_WhenCustomerRole() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{id}", OTHER_CUSTOMER_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void search_Returns403_WhenCustomerRole() throws Exception {
        mockMvc.perform(get("/api/v1/customers/search")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .param("email", "victim@bank.com"))
                .andExpect(status().isForbidden());
    }

    // ── Rule: IDOR — customer must not enumerate other customers by UUID ──────
    //    GET /customers/{id} is staff-only; a CUSTOMER JWT must get 403,
    //    not 200 or 404 (which would reveal existence).

    @Test
    void getById_Returns403_NotLeakingExistence_WhenCustomerTriesOtherUserById() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{id}", OTHER_CUSTOMER_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    // ── Rule: Malformed UUID path variable must be rejected gracefully ────────

    @Test
    void getById_Returns400_WhenIdIsNotUuid() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{id}", "not-a-valid-uuid")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TELLER"))))
                .andExpect(status().isBadRequest());
    }

    // ── Rule: Staff roles that ARE allowed to access GET /{id} ───────────────

    @Test
    void getById_Returns200_WhenTellerRole() throws Exception {
        com.rewabank.customers.dto.CustomerResponse mockResponse =
                new com.rewabank.customers.dto.CustomerResponse(
                        OTHER_CUSTOMER_ID, "kc-1", "john@test.com", "John Doe",
                        "******9999", null, null, null, null, null, null, null, null, null, null);
        when(customerService.getById(any())).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/customers/{id}", OTHER_CUSTOMER_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TELLER"))))
                .andExpect(status().isOk());
    }

    @Test
    void search_Returns200_WhenBranchManagerRole() throws Exception {
        when(customerService.searchByEmail(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/customers/search")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BRANCH_MANAGER")))
                        .param("email", "staff@bank.com"))
                .andExpect(status().isOk());
    }

    // ── Rule: Empty search parameters return empty list, not 500 ─────────────

    @Test
    void search_Returns200EmptyList_WhenNoParamsProvided() throws Exception {
        mockMvc.perform(get("/api/v1/customers/search")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TELLER"))))
                .andExpect(status().isOk());
    }
}
