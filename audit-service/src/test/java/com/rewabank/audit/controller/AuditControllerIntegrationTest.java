package com.rewabank.audit.controller;

import com.rewabank.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditControllerIntegrationTest {

    @Autowired MockMvc mockMvc;

    @SuppressWarnings("deprecation")
    @MockBean AuditLogRepository auditLogRepository;

    private static final String USER_ID       = "kc-user-audit-001";
    private static final String AGGREGATE_ID  = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        when(auditLogRepository.findByKeycloakUserIdOrderByRecordedAtDesc(any(), any()))
                .thenReturn(Page.empty());
        when(auditLogRepository.findByAggregateIdOrderByRecordedAtDesc(any(), any()))
                .thenReturn(Page.empty());
        when(auditLogRepository.findByEventTypeOrderByRecordedAtDesc(any(), any()))
                .thenReturn(Page.empty());
        when(auditLogRepository.findByDateRange(any(), any(), any()))
                .thenReturn(Page.empty());
    }

    private void authenticateAs(String role) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "audit-caller")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt,
                        List.of(new SimpleGrantedAuthority(role))));
    }

    // ── GET /api/v1/audit/user/{keycloakUserId} ───────────────────────────────

    @Test
    void getByUser_Returns200_WhenAuditorRole() throws Exception {
        authenticateAs("ROLE_AUDITOR");

        mockMvc.perform(get("/api/v1/audit/user/{userId}", USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    void getByUser_Returns200_WhenSuperAdminRole() throws Exception {
        authenticateAs("ROLE_SUPER_ADMIN");

        mockMvc.perform(get("/api/v1/audit/user/{userId}", USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    void getByUser_Returns403_WhenCustomerRole() throws Exception {
        authenticateAs("ROLE_CUSTOMER");

        mockMvc.perform(get("/api/v1/audit/user/{userId}", USER_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void getByUser_Returns401_WhenUnauthenticated() throws Exception {
        // No authentication set

        mockMvc.perform(get("/api/v1/audit/user/{userId}", USER_ID))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/v1/audit/aggregate/{aggregateId} ─────────────────────────────

    @Test
    void getByAggregate_Returns200_WhenAuditorRole() throws Exception {
        authenticateAs("ROLE_AUDITOR");

        mockMvc.perform(get("/api/v1/audit/aggregate/{aggregateId}", AGGREGATE_ID))
                .andExpect(status().isOk());
    }

    @Test
    void getByAggregate_Returns403_WhenBranchManagerRole() throws Exception {
        // BRANCH_MANAGER is NOT an allowed role for audit endpoints
        authenticateAs("ROLE_BRANCH_MANAGER");

        mockMvc.perform(get("/api/v1/audit/aggregate/{aggregateId}", AGGREGATE_ID))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/v1/audit/event-type/{eventType} ──────────────────────────────

    @Test
    void getByEventType_Returns200_WhenSuperAdmin() throws Exception {
        authenticateAs("ROLE_SUPER_ADMIN");

        mockMvc.perform(get("/api/v1/audit/event-type/{eventType}", "ACCOUNT_ACTIVATED"))
                .andExpect(status().isOk());
    }

    // ── GET /api/v1/audit/date-range ──────────────────────────────────────────

    @Test
    void getByDateRange_Returns200_WhenAuditorRole() throws Exception {
        authenticateAs("ROLE_AUDITOR");

        mockMvc.perform(get("/api/v1/audit/date-range")
                        .param("from", "2025-01-01T00:00:00")
                        .param("to",   "2025-12-31T23:59:59"))
                .andExpect(status().isOk());
    }

    @Test
    void getByDateRange_Returns400_WhenFromParamMissing() throws Exception {
        authenticateAs("ROLE_AUDITOR");

        mockMvc.perform(get("/api/v1/audit/date-range")
                        .param("to", "2025-12-31T23:59:59"))
                .andExpect(status().isBadRequest());
    }
}
