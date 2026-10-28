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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization + input tests for AuditController (read-only, AUDITOR/SUPER_ADMIN).
 *
 * Runs with the security filter chain ON and drives auth via
 * {@code .with(jwt().authorities(...))} so SecurityConfig's {@code hasAnyRole}
 * rules resolve correctly: unauthenticated → 401, disallowed role → 403,
 * allowed role → 200. (Setting SecurityContextHolder manually does not survive
 * the filter chain, which is why that approach is avoided here.)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditControllerIntegrationTest {

    @Autowired MockMvc mockMvc;

    @SuppressWarnings("deprecation")
    @MockBean AuditLogRepository auditLogRepository;

    private static final String USER_ID      = "kc-user-audit-001";
    private static final String AGGREGATE_ID = UUID.randomUUID().toString();

    private static SimpleGrantedAuthority role(String r) {
        return new SimpleGrantedAuthority(r);
    }

    @BeforeEach
    void setUp() {
        when(auditLogRepository.findByKeycloakUserIdOrderByRecordedAtDesc(any(), any()))
                .thenReturn(Page.empty());
        when(auditLogRepository.findByAggregateIdOrderByRecordedAtDesc(any(), any()))
                .thenReturn(Page.empty());
        when(auditLogRepository.findByEventTypeOrderByRecordedAtDesc(any(), any()))
                .thenReturn(Page.empty());
        when(auditLogRepository.findByDateRange(any(), any(), any()))
                .thenReturn(Page.empty());
    }

    // ── GET /api/v1/audit/user/{keycloakUserId} ───────────────────────────────

    @Test
    void getByUser_Returns200_WhenAuditorRole() throws Exception {
        mockMvc.perform(get("/api/v1/audit/user/{userId}", USER_ID)
                        .with(jwt().authorities(role("ROLE_AUDITOR"))))
                .andExpect(status().isOk());
    }

    @Test
    void getByUser_Returns200_WhenSuperAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/audit/user/{userId}", USER_ID)
                        .with(jwt().authorities(role("ROLE_SUPER_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void getByUser_Returns403_WhenCustomerRole() throws Exception {
        mockMvc.perform(get("/api/v1/audit/user/{userId}", USER_ID)
                        .with(jwt().authorities(role("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getByUser_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/audit/user/{userId}", USER_ID))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/v1/audit/aggregate/{aggregateId} ─────────────────────────────

    @Test
    void getByAggregate_Returns200_WhenAuditorRole() throws Exception {
        mockMvc.perform(get("/api/v1/audit/aggregate/{aggregateId}", AGGREGATE_ID)
                        .with(jwt().authorities(role("ROLE_AUDITOR"))))
                .andExpect(status().isOk());
    }

    @Test
    void getByAggregate_Returns403_WhenBranchManagerRole() throws Exception {
        // BRANCH_MANAGER is NOT an allowed role for audit endpoints
        mockMvc.perform(get("/api/v1/audit/aggregate/{aggregateId}", AGGREGATE_ID)
                        .with(jwt().authorities(role("ROLE_BRANCH_MANAGER"))))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/v1/audit/event-type/{eventType} ──────────────────────────────

    @Test
    void getByEventType_Returns200_WhenSuperAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/audit/event-type/{eventType}", "ACCOUNT_ACTIVATED")
                        .with(jwt().authorities(role("ROLE_SUPER_ADMIN"))))
                .andExpect(status().isOk());
    }

    // ── GET /api/v1/audit/date-range ──────────────────────────────────────────

    @Test
    void getByDateRange_Returns200_WhenAuditorRole() throws Exception {
        mockMvc.perform(get("/api/v1/audit/date-range")
                        .with(jwt().authorities(role("ROLE_AUDITOR")))
                        .param("from", "2025-01-01T00:00:00")
                        .param("to",   "2025-12-31T23:59:59"))
                .andExpect(status().isOk());
    }

    @Test
    void getByDateRange_Returns400_WhenFromParamMissing() throws Exception {
        mockMvc.perform(get("/api/v1/audit/date-range")
                        .with(jwt().authorities(role("ROLE_AUDITOR")))
                        .param("to", "2025-12-31T23:59:59"))
                .andExpect(status().isBadRequest());
    }
}