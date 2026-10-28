package com.rewabank.transactions.controller;

import com.rewabank.transactions.client.AccountsFeignClient;
import com.rewabank.transactions.client.FraudFeignClient;
import com.rewabank.transactions.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role-based authorization tests for TransactionController.
 *
 * Runs with the security filter chain ON so that SecurityConfig's request-level
 * {@code hasAnyRole} rules and the controllers' {@code @PreAuthorize} checks are
 * exercised: unauthenticated → 401, wrong role → 403, allowed role → 200. Pure
 * bean-validation (400/415) lives in {@link TransactionControllerSecurityTest},
 * which disables filters.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionControllerAuthorizationTest {

    @Autowired MockMvc mockMvc;

    @SuppressWarnings("deprecation") @MockBean TransactionService     transactionService;
    @SuppressWarnings("deprecation") @MockBean AccountsFeignClient    accountsFeignClient;
    @SuppressWarnings("deprecation") @MockBean FraudFeignClient       fraudFeignClient;
    @SuppressWarnings("deprecation") @MockBean RedisTemplate<String, Object> redisTemplate;

    // ── Rule: Unauthenticated requests must be rejected (401) ────────────────

    @Test
    void transfer_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyTransactions_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getById_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── Rule: Staff-only endpoints must reject CUSTOMER/TELLER (403) ─────────

    @Test
    void getAllTransactions_Returns403_WhenCustomerRole() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/all")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void reverse_Returns403_WhenCustomerRole() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/{id}/reverse", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void reverse_Returns403_WhenTellerRole() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/{id}/reverse", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TELLER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isForbidden());
    }

    // ── Rule: BRANCH_MANAGER / SUPER_ADMIN can access staff endpoints (200) ──

    @Test
    void getAllTransactions_Returns200_WhenBranchManager() throws Exception {
        when(transactionService.getAllTransactions(any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/transactions/all")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BRANCH_MANAGER"))))
                .andExpect(status().isOk());
    }

    @Test
    void getAllTransactions_Returns200_WhenSuperAdmin() throws Exception {
        when(transactionService.getAllTransactions(any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/transactions/all")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))))
                .andExpect(status().isOk());
    }
}