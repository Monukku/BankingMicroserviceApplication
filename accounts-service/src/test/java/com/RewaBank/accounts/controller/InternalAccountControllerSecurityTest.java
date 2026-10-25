package com.rewabank.accounts.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.accounts.client.CustomersFeignClient;
import com.rewabank.accounts.config.InternalCallFilter;
import com.rewabank.accounts.dto.AccountDebitCreditRequest;
import com.rewabank.accounts.dto.AccountResponse;
import com.rewabank.accounts.entity.Account;
import com.rewabank.accounts.services.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalAccountControllerSecurityTest {

    private static final String INTERNAL_SECRET = "test-internal-secret";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @SuppressWarnings("deprecation") @MockBean private AccountService accountService;
    @SuppressWarnings("deprecation") @MockBean private CustomersFeignClient customersFeignClient;
    @SuppressWarnings("deprecation") @MockBean private RedisTemplate<String, Object> redisTemplate;

    private static final UUID ACCOUNT_ID = UUID.randomUUID();

    private AccountDebitCreditRequest debitRequest() {
        return new AccountDebitCreditRequest(ACCOUNT_ID, new BigDecimal("5000.00"), "txn-123");
    }

    private AccountResponse stubResponse() {
        return new AccountResponse(ACCOUNT_ID, "123456789012", "user-1",
                UUID.randomUUID(), Account.AccountType.SAVINGS, Account.AccountStatus.ACTIVE,
                new BigDecimal("45000.00"), "INR", "BR001", "IFSC001",
                LocalDateTime.now(), LocalDateTime.now(),
                null, null, null, null, null, null, null, null, null, null);
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void debit_Returns200_WithValidJwtAndCorrectHeader() throws Exception {
        when(accountService.debit(any(), any(), any())).thenReturn(stubResponse());

        mockMvc.perform(patch("/api/v1/accounts/{id}/debit", ACCOUNT_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TRANSACTIONS_MS")))
                        .header(InternalCallFilter.HEADER_NAME, INTERNAL_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debitRequest())))
                .andExpect(status().isOk());
    }

    @Test
    void credit_Returns200_WithValidJwtAndCorrectHeader() throws Exception {
        when(accountService.credit(any(), any(), any())).thenReturn(stubResponse());

        mockMvc.perform(patch("/api/v1/accounts/{id}/credit", ACCOUNT_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TRANSACTIONS_MS")))
                        .header(InternalCallFilter.HEADER_NAME, INTERNAL_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debitRequest())))
                .andExpect(status().isOk());
    }

    // Any authenticated service propagating a user JWT (CUSTOMER, LOANS_MS, etc.) is also allowed
    // as long as it carries the correct internal header — role is not the gate here.
    @Test
    void debit_Returns200_WhenAnyRoleWithCorrectHeader() throws Exception {
        when(accountService.debit(any(), any(), any())).thenReturn(stubResponse());

        mockMvc.perform(patch("/api/v1/accounts/{id}/debit", ACCOUNT_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .header(InternalCallFilter.HEADER_NAME, INTERNAL_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debitRequest())))
                .andExpect(status().isOk());
    }

    // ── Missing / wrong header (layer 1) ─────────────────────────────────────

    @Test
    void debit_Returns403_WhenInternalHeaderMissing() throws Exception {
        mockMvc.perform(patch("/api/v1/accounts/{id}/debit", ACCOUNT_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TRANSACTIONS_MS")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debitRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void credit_Returns403_WhenInternalHeaderMissing() throws Exception {
        mockMvc.perform(patch("/api/v1/accounts/{id}/credit", ACCOUNT_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TRANSACTIONS_MS")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debitRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void debit_Returns403_WhenInternalHeaderIsWrong() throws Exception {
        mockMvc.perform(patch("/api/v1/accounts/{id}/debit", ACCOUNT_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TRANSACTIONS_MS")))
                        .header(InternalCallFilter.HEADER_NAME, "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debitRequest())))
                .andExpect(status().isForbidden());
    }

    // ── No JWT (layer 2) ──────────────────────────────────────────────────────

    @Test
    void debit_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(patch("/api/v1/accounts/{id}/debit", ACCOUNT_ID)
                        .header(InternalCallFilter.HEADER_NAME, INTERNAL_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debitRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void credit_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(patch("/api/v1/accounts/{id}/credit", ACCOUNT_ID)
                        .header(InternalCallFilter.HEADER_NAME, INTERNAL_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debitRequest())))
                .andExpect(status().isUnauthorized());
    }
}