package com.rewabank.fraud.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.fraud.service.FraudAlertService;
import com.rewabank.fraud.service.FraudScoringService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Negative / security tests for FraudController.
 * Covers: unauthenticated access to scoring endpoint, role-based access
 * control on alert management, and malformed parameter rejection.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FraudControllerSecurityTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    @SuppressWarnings("deprecation") @MockBean FraudScoringService fraudScoringService;
    @SuppressWarnings("deprecation") @MockBean FraudAlertService   fraudAlertService;
    @SuppressWarnings("deprecation") @MockBean RedisTemplate<String, Object> redisTemplate;
    @SuppressWarnings("deprecation") @MockBean KafkaTemplate<String, Object> kafkaTemplate;

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID ALERT_ID   = UUID.randomUUID();

    // ── Rule: Unauthenticated access must always return 401 ──────────────────
    // Note: GET /api/v1/fraud/score is intentionally permitAll() — scoring is an
    // internal endpoint guarded by Istio mTLS (transactions-ms-sa), not JWT — so it
    // has no unauthenticated-401 case. Alert-management endpoints below do.

    @Test
    void getOpenAlerts_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/fraud/alerts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAlertsByAccount_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/fraud/alerts/account/{accountId}", ACCOUNT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resolveAlert_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(patch("/api/v1/fraud/alerts/{alertId}/resolve", ALERT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"test\",\"resolution\":\"RESOLVED\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── Rule: CUSTOMER role must be denied from alert management endpoints ───

    @Test
    void getOpenAlerts_Returns403_WhenCustomerRole() throws Exception {
        mockMvc.perform(get("/api/v1/fraud/alerts")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAlertsByAccount_Returns403_WhenCustomerRole() throws Exception {
        mockMvc.perform(get("/api/v1/fraud/alerts/account/{accountId}", ACCOUNT_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void resolveAlert_Returns403_WhenCustomerRole() throws Exception {
        mockMvc.perform(patch("/api/v1/fraud/alerts/{alertId}/resolve", ALERT_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"test\",\"resolution\":\"RESOLVED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void resolveAlert_Returns403_WhenTellerRole() throws Exception {
        mockMvc.perform(patch("/api/v1/fraud/alerts/{alertId}/resolve", ALERT_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TELLER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"test\",\"resolution\":\"RESOLVED\"}"))
                .andExpect(status().isForbidden());
    }

    // ── Rule: Malformed / missing required query parameters must be rejected ──

    @Test
    void getScore_Returns400_WhenAccountIdMissing() throws Exception {
        mockMvc.perform(get("/api/v1/fraud/score")
                        .with(jwt())
                        .param("amount",          "5000.00")
                        .param("transactionType", "TRANSFER")
                        .param("correlationId",   "corr-002"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getScore_Returns400_WhenAmountMissing() throws Exception {
        mockMvc.perform(get("/api/v1/fraud/score")
                        .with(jwt())
                        .param("accountId",       ACCOUNT_ID.toString())
                        .param("transactionType", "TRANSFER")
                        .param("correlationId",   "corr-003"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getScore_Returns400_WhenAccountIdIsNotUuid() throws Exception {
        mockMvc.perform(get("/api/v1/fraud/score")
                        .with(jwt())
                        .param("accountId",       "not-a-valid-uuid")
                        .param("amount",          "5000.00")
                        .param("transactionType", "TRANSFER")
                        .param("correlationId",   "corr-004"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getScore_Returns400_WhenAmountIsNegative() throws Exception {
        mockMvc.perform(get("/api/v1/fraud/score")
                        .with(jwt())
                        .param("accountId",       ACCOUNT_ID.toString())
                        .param("amount",          "-500.00")
                        .param("transactionType", "TRANSFER")
                        .param("correlationId",   "corr-005"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAlertsByAccount_Returns400_WhenAccountIdIsNotUuid() throws Exception {
        mockMvc.perform(get("/api/v1/fraud/alerts/account/{accountId}", "not-a-uuid")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BRANCH_MANAGER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resolveAlert_Returns400_WhenAlertIdIsNotUuid() throws Exception {
        mockMvc.perform(patch("/api/v1/fraud/alerts/{alertId}/resolve", "bad-uuid")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BRANCH_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"test\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── Rule: Authenticated BRANCH_MANAGER can access alert endpoints ────────

    @Test
    void getOpenAlerts_Returns200_WhenBranchManager() throws Exception {
        when(fraudAlertService.getOpenAlerts(any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        mockMvc.perform(get("/api/v1/fraud/alerts")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BRANCH_MANAGER"))))
                .andExpect(status().isOk());
    }

    @Test
    void getOpenAlerts_Returns200_WhenSuperAdmin() throws Exception {
        when(fraudAlertService.getOpenAlerts(any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        mockMvc.perform(get("/api/v1/fraud/alerts")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))))
                .andExpect(status().isOk());
    }

    // ── Rule: /score endpoint is accessible to any authenticated user ────────
    //    (internal Feign calls carry a JWT — no role restriction on scoring)

    @Test
    void getScore_Returns200_WhenAuthenticatedWithAnyRole() throws Exception {
        when(fraudScoringService.score(any(), any(), any(), any()))
                .thenReturn(new FraudScoringService.ScoringResult(10, "APPROVE", ""));

        mockMvc.perform(get("/api/v1/fraud/score")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .param("accountId",       ACCOUNT_ID.toString())
                        .param("amount",          "5000.00")
                        .param("transactionType", "TRANSFER")
                        .param("correlationId",   "corr-happy"))
                .andExpect(status().isOk());
    }
}
