package com.rewabank.fraud.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.fraud.model.FraudAlert;
import com.rewabank.fraud.service.FraudAlertService;
import com.rewabank.fraud.service.FraudScoringService;
import io.micrometer.core.instrument.MeterRegistry;
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
class FraudControllerIntegrationTest {

    @Autowired MockMvc       mockMvc;
    @Autowired ObjectMapper  objectMapper;

    @SuppressWarnings("deprecation") @MockBean FraudScoringService           fraudScoringService;
    @SuppressWarnings("deprecation") @MockBean FraudAlertService             fraudAlertService;
    @SuppressWarnings("deprecation") @MockBean RedisTemplate<String, Object> redisTemplate;
    @SuppressWarnings("deprecation") @MockBean MeterRegistry                 meterRegistry;

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID ALERT_ID   = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "fraud-test-user")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt,
                        List.of(new SimpleGrantedAuthority("ROLE_BRANCH_MANAGER"))));
    }

    // ── GET /api/v1/fraud/score ───────────────────────────────────────────────

    @Test
    void getScore_Returns200_WhenValidParams() throws Exception {
        when(fraudScoringService.score(eq(ACCOUNT_ID), any(), any(), any()))
                .thenReturn(new FraudScoringService.ScoringResult(10, "APPROVE", ""));

        mockMvc.perform(get("/api/v1/fraud/score")
                        .param("accountId",       ACCOUNT_ID.toString())
                        .param("amount",          "1000.00")
                        .param("transactionType", "TRANSFER")
                        .param("correlationId",   "corr-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("APPROVE"))
                .andExpect(jsonPath("$.score").value(10));
    }

    @Test
    void getScore_Returns400_WhenAccountIdMissing() throws Exception {
        mockMvc.perform(get("/api/v1/fraud/score")
                        .param("amount",          "1000.00")
                        .param("transactionType", "TRANSFER")
                        .param("correlationId",   "corr-002"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/v1/fraud/alerts ─────────────────────────────────────────────

    @Test
    void getOpenAlerts_Returns200_WhenBranchManager() throws Exception {
        when(fraudAlertService.getOpenAlerts(any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/fraud/alerts"))
                .andExpect(status().isOk());
    }

    @Test
    void getOpenAlerts_Returns403_WhenCustomerRole() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "customer-user")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt,
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));

        mockMvc.perform(get("/api/v1/fraud/alerts"))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/v1/fraud/alerts/account/{accountId} ─────────────────────────

    @Test
    void getAlertsByAccount_Returns200_WhenBranchManager() throws Exception {
        when(fraudAlertService.getByAccount(eq(ACCOUNT_ID), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/fraud/alerts/account/{accountId}", ACCOUNT_ID))
                .andExpect(status().isOk());
    }

    // ── PATCH /api/v1/fraud/alerts/{alertId}/resolve ─────────────────────────

    @Test
    void resolveAlert_Returns200_WhenBranchManager() throws Exception {
        FraudAlert mockAlert = FraudAlert.builder()
                .id(ALERT_ID)
                .accountId(ACCOUNT_ID)
                .action("FLAG")
                .amount(new BigDecimal("5000.00"))
                .fraudScore(65)
                .triggeredRules("HIGH_VELOCITY")
                .status(FraudAlert.AlertStatus.RESOLVED)
                .createdAt(LocalDateTime.now())
                .build();

        when(fraudAlertService.resolveAlert(eq(ALERT_ID), any(), any(), any()))
                .thenReturn(mockAlert);

        mockMvc.perform(patch("/api/v1/fraud/alerts/{alertId}/resolve", ALERT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"Resolved - false positive\",\"resolution\":\"RESOLVED\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void resolveAlert_Returns403_WhenCustomerRole() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "customer-user")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt,
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));

        mockMvc.perform(patch("/api/v1/fraud/alerts/{alertId}/resolve", ALERT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"test\"}"))
                .andExpect(status().isForbidden());
    }
}
