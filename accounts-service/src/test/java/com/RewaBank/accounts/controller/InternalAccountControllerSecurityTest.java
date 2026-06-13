package com.RewaBank.accounts.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.accounts.client.CustomersFeignClient;
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
                LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void debit_ShouldReturn200_WhenCallerHasTransactionsMsRole() throws Exception {
        when(accountService.debit(any(), any(), any())).thenReturn(stubResponse());

        mockMvc.perform(patch("/api/v1/accounts/{id}/debit", ACCOUNT_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TRANSACTIONS_MS")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debitRequest())))
                .andExpect(status().isOk());
    }

    @Test
    void credit_ShouldReturn200_WhenCallerHasTransactionsMsRole() throws Exception {
        when(accountService.credit(any(), any(), any())).thenReturn(stubResponse());

        mockMvc.perform(patch("/api/v1/accounts/{id}/credit", ACCOUNT_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TRANSACTIONS_MS")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debitRequest())))
                .andExpect(status().isOk());
    }

    // Access control for debit/credit is enforced by Istio AuthorizationPolicy
    // (restricts to transactions-ms-sa + loans-ms-sa service accounts via mTLS).
    // Spring Security uses permitAll() here so any authenticated caller reaches
    // the handler — Istio blocks unauthorised services before they reach the pod.

    @Test
    void debit_ShouldReturn200_WhenCallerIsCustomer() throws Exception {
        when(accountService.debit(any(), any(), any())).thenReturn(stubResponse());

        mockMvc.perform(patch("/api/v1/accounts/{id}/debit", ACCOUNT_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debitRequest())))
                .andExpect(status().isOk());
    }

    @Test
    void credit_ShouldReturn200_WhenCallerIsCustomer() throws Exception {
        when(accountService.credit(any(), any(), any())).thenReturn(stubResponse());

        mockMvc.perform(patch("/api/v1/accounts/{id}/credit", ACCOUNT_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debitRequest())))
                .andExpect(status().isOk());
    }

    @Test
    void debit_ShouldReturn200_WhenCallerIsBranchManager() throws Exception {
        when(accountService.debit(any(), any(), any())).thenReturn(stubResponse());

        mockMvc.perform(patch("/api/v1/accounts/{id}/debit", ACCOUNT_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BRANCH_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debitRequest())))
                .andExpect(status().isOk());
    }

    @Test
    void debit_ShouldReturn200_WhenUnauthenticated() throws Exception {
        when(accountService.debit(any(), any(), any())).thenReturn(stubResponse());

        mockMvc.perform(patch("/api/v1/accounts/{id}/debit", ACCOUNT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debitRequest())))
                .andExpect(status().isOk());
    }
}
