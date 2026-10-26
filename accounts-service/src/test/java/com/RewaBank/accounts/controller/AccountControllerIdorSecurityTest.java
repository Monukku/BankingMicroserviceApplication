package com.rewabank.accounts.controller;

import com.rewabank.accounts.client.CustomersFeignClient;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IDOR, header-injection, and JWT security tests for AccountController.
 *
 * Authentication is driven by real JWTs via {@link org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors#jwt()}
 * so the actual security filter chain + method security run (a SecurityContextHolder
 * set inside a test does NOT propagate into a MockMvc request).
 *
 * Threat model:
 *   - A customer may read only their OWN account; another customer's UUID → 404 (no existence leak).
 *   - Staff and the INTERNAL_SERVICE identity may read any account.
 *   - Staff-only mutations (freeze/activate) → 403 for a customer.
 *   - Unauthenticated → 401; malformed path variable → 400 (never 500).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountControllerIdorSecurityTest {

    @Autowired MockMvc mockMvc;
    @Autowired AccountsRepository accountsRepository;

    @SuppressWarnings("deprecation") @MockBean AccountReadService            accountReadService;
    @SuppressWarnings("deprecation") @MockBean CustomersFeignClient          customersFeignClient;
    @SuppressWarnings("deprecation") @MockBean RedisTemplate<String, Object> redisTemplate;

    private Account userAAccount;
    private final String userAId = "user-a-001";
    private final String userBId = "user-b-002";

    @BeforeEach
    void setUp() {
        accountsRepository.deleteAll();
        accountsRepository.flush();
        userAAccount = accountsRepository.saveAndFlush(
                Account.builder()
                        .accountNumber("100000000001")
                        .keycloakUserId(userAId)
                        .customerId(UUID.randomUUID())
                        .accountType(Account.AccountType.SAVINGS)
                        .balance(BigDecimal.valueOf(5000))
                        .minimumBalance(BigDecimal.valueOf(1000))
                        .currency("INR")
                        .status(Account.AccountStatus.ACTIVE)
                        .branchCode("BR001")
                        .ifscCode("RWAB001")
                        .build());
    }

    /** JWT with the given subject + realm roles: authorities (ROLE_*) drive the filter
     *  chain / @PreAuthorize; the realm_access claim drives AccountAccessPolicy. */
    private static RequestPostProcessor as(String subject, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        return jwt()
                .jwt(j -> j.subject(subject).claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(authorities);
    }

    // ── GET /{uuid}: authenticated + (owner | staff | INTERNAL_SERVICE) ──────────

    @Test
    void getAccountById_Returns200_WhenOwner() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", userAAccount.getId()).with(as(userAId, "CUSTOMER")))
                .andExpect(status().isOk());
    }

    @Test
    void getAccountById_Returns404_WhenOtherCustomer() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", userAAccount.getId()).with(as(userBId, "CUSTOMER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAccountById_Returns200_WhenStaff() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", userAAccount.getId()).with(as("staff-1", "BRANCH_MANAGER")))
                .andExpect(status().isOk());
    }

    @Test
    void getAccountById_Returns200_WhenInternalService() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", userAAccount.getId()).with(as("svc-cards", "INTERNAL_SERVICE")))
                .andExpect(status().isOk());
    }

    @Test
    void getAccountById_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", userAAccount.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAccountById_Returns400_WhenIdIsNotUuid() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", "not-a-uuid").with(as("u", "CUSTOMER")))
                .andExpect(status().isBadRequest());
    }

    // ── Staff-only mutations: a customer must get 403 ────────────────────────────

    @Test
    void freezeAccount_Returns403_WhenCustomer() throws Exception {
        mockMvc.perform(patch("/api/v1/accounts/{id}/freeze", userAAccount.getId())
                        .with(as(userBId, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"malicious freeze\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void activateAccount_Returns403_WhenCustomer() throws Exception {
        mockMvc.perform(patch("/api/v1/accounts/{id}/activate", userAAccount.getId())
                        .with(as(userBId, "CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    // ── Unauthenticated → 401 ────────────────────────────────────────────────────

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
    void freezeAccount_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(patch("/api/v1/accounts/{id}/freeze", userAAccount.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── Malformed path variable → 400 (reached only after passing authz) ─────────

    @Test
    void freezeAccount_Returns400_WhenIdIsNotUuid() throws Exception {
        mockMvc.perform(patch("/api/v1/accounts/{id}/freeze", "sql-injection'; DROP TABLE accounts;--")
                        .with(as("mgr", "BRANCH_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── Forged X-User-Id is ignored — identity comes from the JWT sub ────────────

    @Test
    void getMyAccounts_IgnoresForgedXUserIdHeader_ReturnsOnlyCallerAccounts() throws Exception {
        // Caller is User B (owns nothing); forges X-User-Id = User A. Controller uses
        // the JWT subject (User B), so the forged header is ignored → empty list.
        mockMvc.perform(get("/api/v1/accounts/my-accounts")
                        .with(as(userBId, "CUSTOMER"))
                        .header("X-User-Id", userAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── Tampered / malformed bearer tokens → 401 ─────────────────────────────────

    @Test
    void getMyAccounts_Returns401_WhenBearerTokenIsMalformed() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/my-accounts")
                        .header("Authorization", "Bearer this.is.not.a.valid.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createAccount_Returns401_WhenAuthorizationSchemeIsBasicNotBearer() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Basic dXNlcjpwYXNz")
                        .header("X-Customer-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"SAVINGS\",\"branchCode\":\"BR001\",\"ifscCode\":\"RWAB001\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── Missing required X-Customer-Id header → 400 (after authentication) ───────

    @Test
    void createAccount_Returns400_WhenXCustomerIdHeaderMissing() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .with(as(userAId, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"SAVINGS\",\"branchCode\":\"BR001\",\"ifscCode\":\"RWAB001\"}"))
                .andExpect(status().isBadRequest());
    }
}