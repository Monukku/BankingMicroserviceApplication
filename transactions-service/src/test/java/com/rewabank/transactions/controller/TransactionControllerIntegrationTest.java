package com.rewabank.transactions.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.transactions.client.AccountsFeignClient;
import com.rewabank.transactions.client.FraudFeignClient;
import com.rewabank.transactions.dto.TransactionResponse;
import com.rewabank.transactions.entity.Transaction;
import com.rewabank.transactions.exception.TransactionException;
import com.rewabank.transactions.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class TransactionControllerIntegrationTest {

    @Autowired MockMvc       mockMvc;
    @Autowired ObjectMapper  objectMapper;

    @SuppressWarnings("deprecation") @MockBean TransactionService             transactionService;
    @SuppressWarnings("deprecation") @MockBean AccountsFeignClient             accountsFeignClient;
    @SuppressWarnings("deprecation") @MockBean FraudFeignClient                fraudFeignClient;
    @SuppressWarnings("deprecation") @MockBean RedisTemplate<String, Object>   redisTemplate;

    private static final String USER_ID = "txn-test-user-001";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", USER_ID)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt,
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
    }

    private TransactionResponse sampleResponse() {
        return new TransactionResponse(
                UUID.randomUUID(), "idem-001",
                "111111111111", "222222222222",
                new BigDecimal("1000.00"), "INR",
                Transaction.TransactionType.TRANSFER,
                Transaction.TransactionStatus.COMPLETED,
                "test", "REF001", "APPROVE",
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    // ── GET /api/v1/transactions ───────────────────────────────────────────────

    @Test
    void getMyTransactions_Returns200_WhenAuthenticated() throws Exception {
        when(transactionService.getMyTransactions(eq(USER_ID), any()))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isOk());
    }

    // ── GET /api/v1/transactions/{id} ─────────────────────────────────────────

    @Test
    void getById_Returns200_WhenOwnerAndExists() throws Exception {
        UUID id = UUID.randomUUID();
        when(transactionService.getById(eq(id), eq(USER_ID)))
                .thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/transactions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("INR"));
    }

    @Test
    void getById_Returns404_WhenTransactionNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(transactionService.getById(eq(id), eq(USER_ID)))
                .thenThrow(new TransactionException("TXN_007", "Transaction not found"));

        mockMvc.perform(get("/api/v1/transactions/{id}", id))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/v1/transactions/all — BRANCH_MANAGER only ───────────────────

    @Test
    void getAllTransactions_Returns200_WhenBranchManager() throws Exception {
        // Re-authenticate as BRANCH_MANAGER
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", USER_ID)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt,
                        List.of(new SimpleGrantedAuthority("ROLE_BRANCH_MANAGER"))));

        when(transactionService.getAllTransactions(any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/transactions/all"))
                .andExpect(status().isOk());
    }

    @Test
    void getAllTransactions_Returns403_WhenCustomerRole() throws Exception {
        // CUSTOMER already set in @BeforeEach
        mockMvc.perform(get("/api/v1/transactions/all"))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/v1/transactions/transfer — missing idempotency key ──────────

    @Test
    void transfer_Returns400_WhenIdempotencyKeyMissing() throws Exception {
        String body = objectMapper.writeValueAsString(
                new com.rewabank.transactions.dto.TransferRequest(
                        UUID.randomUUID(), "111111111111",
                        UUID.randomUUID(), "222222222222",
                        new BigDecimal("500.00"), "test", null
                ));

        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/v1/transactions/{id}/reverse — BRANCH_MANAGER only ─────────

    @Test
    void reverse_Returns403_WhenCustomerRole() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/{id}/reverse", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isForbidden());
    }
}
