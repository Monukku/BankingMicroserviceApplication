package com.rewabank.accounts.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.accounts.client.CustomersFeignClient;
import com.rewabank.accounts.dto.KycStatusResponse;
import com.rewabank.accounts.entity.Account;
import com.rewabank.accounts.repository.AccountsRepository;
import com.rewabank.accounts.services.AccountReadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IDOR, header injection, and JWT security tests for AccountController.
 *
 * Threat model:
 *   - User A must not read or mutate User B's account by guessing its UUID.
 *   - Callers must not forge X-User-Id to impersonate another user.
 *   - Malformed/expired JWTs must always produce 401, never 200.
 *   - Malformed path variables must produce 400, never 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountControllerIdorSecurityTest {

    @Autowired MockMvc       mockMvc;
    @Autowired ObjectMapper  objectMapper;
    @Autowired AccountsRepository accountsRepository;

    @SuppressWarnings("deprecation") @MockBean AccountReadService           accountReadService;
    @SuppressWarnings("deprecation") @MockBean CustomersFeignClient         customersFeignClient;
    @SuppressWarnings("deprecation") @MockBean RedisTemplate<String, Object> redisTemplate;

    private Account userAAccount;
    private final String userAId = "user-a-001";
    private final String userBId = "user-b-002";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        accountsRepository.deleteAll();
        accountsRepository.flush();

        // User A owns this account
        userAAccount = accountsRepository.saveAndFlush(
                Account.builder()
                        .accountNumber("ACC-USER-A-0001")
                        .keycloakUserId(userAId)
                        .customerId(UUID.randomUUID())
                        .accountType(Account.AccountType.SAVINGS)
                        .balance(BigDecimal.valueOf(5000))
                        .status(Account.AccountStatus.ACTIVE)
                        .branchCode("BR001")
                        .ifscCode("RWAB001")
                        .build());
    }

    private void authenticateAs(String userId, String... roles) {
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", userId)
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, authorities));
    }

    // ── Rule: IDOR — User B must not read User A's account by UUID ───────────

    @Test
    void getAccountById_Returns404_WhenUserBRequestsUserAAccount() throws Exception {
        authenticateAs(userBId, "ROLE_CUSTOMER");

        // User B requests User A's account UUID — must not return account data
        // Service uses keycloakUserId from JWT, not URL param — returns 404
        mockMvc.perform(get("/api/v1/accounts/{id}", userAAccount.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAccountById_Returns200_WhenUserARequestsOwnAccount() throws Exception {
        authenticateAs(userAId, "ROLE_CUSTOMER");

        mockMvc.perform(get("/api/v1/accounts/{id}", userAAccount.getId()))
                .andExpect(status().isOk());
    }

    // ── Rule: IDOR — User B must not freeze User A's account ─────────────────

    @Test
    void freezeAccount_Returns404_WhenUserBTargetsUserAAccount() throws Exception {
        authenticateAs(userBId, "ROLE_CUSTOMER");

        mockMvc.perform(patch("/api/v1/accounts/{id}/freeze", userAAccount.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"malicious freeze\"}"))
                .andExpect(status().isNotFound());
    }

    // ── Rule: IDOR — User B must not activate User A's account ───────────────

    @Test
    void activateAccount_Returns404_WhenUserBTargetsUserAAccount() throws Exception {
        authenticateAs(userBId, "ROLE_CUSTOMER");

        mockMvc.perform(patch("/api/v1/accounts/{id}/activate", userAAccount.getId()))
                .andExpect(status().isNotFound());
    }

    // ── Rule: Unauthenticated requests must always return 401 ────────────────

    @Test
    void getMyAccounts_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/my-accounts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createAccount_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .header("X-Customer-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"SAVINGS\",\"branchCode\":\"BR001\",\"ifscCode\":\"RWAB001\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAccountById_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", userAAccount.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void freezeAccount_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(patch("/api/v1/accounts/{id}/freeze", userAAccount.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── Rule: Malformed / non-UUID path variables must return 400 ─────────────

    @Test
    void getAccountById_Returns400_WhenIdIsNotUuid() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", "not-a-uuid")
                        .with(jwt()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void freezeAccount_Returns400_WhenIdIsNotUuid() throws Exception {
        mockMvc.perform(patch("/api/v1/accounts/{id}/freeze", "sql-injection'; DROP TABLE accounts;--")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void activateAccount_Returns400_WhenIdIsNotUuid() throws Exception {
        mockMvc.perform(patch("/api/v1/accounts/{id}/activate", "../../../../etc/passwd")
                        .with(jwt()))
                .andExpect(status().isBadRequest());
    }

    // ── Rule: Forged X-User-Id header must be ignored ─────────────────────────
    //    The authoritative user identity comes from the JWT sub claim,
    //    NOT from headers callers can set arbitrarily.

    @Test
    void getMyAccounts_IgnoresForgedXUserIdHeader_ReturnsOnlyCallerAccounts() throws Exception {
        authenticateAs(userBId, "ROLE_CUSTOMER");

        // User B sets X-User-Id to userA to try to see userA's accounts
        // Result: controller reads sub from JWT (userBId), not the forged header
        // → returns User B's accounts (empty), not User A's
        mockMvc.perform(get("/api/v1/accounts/my-accounts")
                        .header("X-User-Id", userAId))
                .andExpect(status().isOk())
                // Response must be an empty array — User B has no accounts
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.length()").value(0));
    }

    // ── Rule: Tampered / malformed JWT must return 401 ────────────────────────

    @Test
    void getMyAccounts_Returns401_WhenBearerTokenIsMalformed() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/my-accounts")
                        .header("Authorization", "Bearer this.is.not.a.valid.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyAccounts_Returns401_WhenBearerTokenIsGibberish() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/my-accounts")
                        .header("Authorization", "Bearer aaaa.bbbb.cccc"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyAccounts_Returns401_WhenBearerHeaderHasNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/my-accounts")
                        .header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createAccount_Returns401_WhenAuthorizationSchemeIsBasicNotBearer() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Basic dXNlcjpwYXNz")  // base64 user:pass
                        .header("X-Customer-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"SAVINGS\",\"branchCode\":\"BR001\",\"ifscCode\":\"RWAB001\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── Rule: Missing required X-Customer-Id header returns 400 ──────────────

    @Test
    void createAccount_Returns400_WhenXCustomerIdHeaderMissing() throws Exception {
        when(customersFeignClient.getKycStatus(any()))
                .thenReturn(new KycStatusResponse(
                        UUID.randomUUID().toString(), userAId,
                        "VERIFIED", true, "OK", "Test", "t@t.com", "9999999999"));

        authenticateAs(userAId, "ROLE_CUSTOMER");

        mockMvc.perform(post("/api/v1/accounts")
                        // no X-Customer-Id header
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"SAVINGS\",\"branchCode\":\"BR001\",\"ifscCode\":\"RWAB001\"}"))
                .andExpect(status().isBadRequest());
    }
}
