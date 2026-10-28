package com.rewabank.transactions.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.transactions.client.AccountsFeignClient;
import com.rewabank.transactions.client.FraudFeignClient;
import com.rewabank.transactions.dto.TransferRequest;
import com.rewabank.transactions.service.TransactionService;
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
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bean-validation / input-rejection tests for TransactionController.
 *
 * Runs with the security filter chain OFF (addFilters = false) so that
 * request-level authorization (SecurityConfig's hasAnyRole rules) does not
 * short-circuit to 403 before the controller's validation runs — these tests
 * assert the 400/415 input-handling contract. Role-based 401/403/200 checks
 * live in {@link TransactionControllerAuthorizationTest}, which keeps filters
 * ON; the two concerns need opposite filter settings, hence the split.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class TransactionControllerSecurityTest {

    @Autowired MockMvc       mockMvc;
    @Autowired ObjectMapper  objectMapper;

    @SuppressWarnings("deprecation") @MockBean TransactionService     transactionService;
    @SuppressWarnings("deprecation") @MockBean AccountsFeignClient    accountsFeignClient;
    @SuppressWarnings("deprecation") @MockBean FraudFeignClient       fraudFeignClient;
    @SuppressWarnings("deprecation") @MockBean RedisTemplate<String, Object> redisTemplate;

    private TransferRequest validTransfer() {
        return new TransferRequest(
                UUID.randomUUID(), "111111111111",
                UUID.randomUUID(), "222222222222",
                new BigDecimal("500.00"), "test transfer", null);
    }

    // ── Rule: X-Idempotency-Key is mandatory on POST /transfer ───────────────

    @Test
    void transfer_Returns400_WhenIdempotencyKeyHeaderMissing() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validTransfer())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_Returns400_WhenIdempotencyKeyIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .with(jwt())
                        .header("X-Idempotency-Key", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validTransfer())))
                .andExpect(status().isBadRequest());
    }

    // ── Rule: Request body validation — missing required fields ──────────────

    @Test
    void transfer_Returns400_WhenRequestBodyMissing() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .with(jwt())
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_Returns400_WhenAmountIsNegative() throws Exception {
        TransferRequest bad = new TransferRequest(
                UUID.randomUUID(), "111111111111",
                UUID.randomUUID(), "222222222222",
                new BigDecimal("-100.00"), "negative amount", null);

        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .with(jwt())
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_Returns400_WhenAmountIsZero() throws Exception {
        TransferRequest bad = new TransferRequest(
                UUID.randomUUID(), "111111111111",
                UUID.randomUUID(), "222222222222",
                BigDecimal.ZERO, "zero amount", null);

        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .with(jwt())
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    // ── Rule: Malformed path variables must be rejected gracefully ───────────

    @Test
    void getById_Returns400_WhenIdIsNotUuid() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/{id}", "not-a-valid-uuid")
                        .with(jwt()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reverse_Returns400_WhenIdIsNotUuid() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/{id}/reverse", "not-a-uuid")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BRANCH_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── Rule: Content-Type must be application/json ──────────────────────────

    @Test
    void transfer_Returns415_WhenContentTypeIsNotJson() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .with(jwt())
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("some plain text"))
                .andExpect(status().isUnsupportedMediaType());
    }

    // ── Rule: Header injection — X-Idempotency-Key with control chars ────────

    @Test
    void transfer_Returns400_WhenIdempotencyKeyContainsNewline() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .with(jwt())
                        .header("X-Idempotency-Key", "key\nX-Injected-Header: attack")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validTransfer())))
                .andExpect(status().isBadRequest());
    }
}