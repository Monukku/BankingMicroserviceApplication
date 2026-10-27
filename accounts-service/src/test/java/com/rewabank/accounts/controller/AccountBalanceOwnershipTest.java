package com.rewabank.accounts.controller;

import com.rewabank.accounts.entity.Account;
import com.rewabank.accounts.repository.AccountsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ownership tests for GET /api/v1/accounts/{accountNumber}/balance.
 *
 * Rule (FINDING 2 regression guard): a customer may read ONLY their own balance.
 *   - owner           -> 200
 *   - other customer  -> 404 (not 403 — must not leak that the account exists)
 *   - staff role      -> 200 (ownership bypass)
 *   - unauthenticated -> 401
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountBalanceOwnershipTest {

    @Autowired MockMvc mockMvc;
    @Autowired AccountsRepository accountsRepository;

    @SuppressWarnings("deprecation") @MockBean RedisTemplate<String, Object> redisTemplate;

    private static final String ACCOUNT_NUMBER = "100000000001";
    private final String ownerSub = "owner-sub-0001";
    private final String otherSub = "other-sub-0002";

    @BeforeEach
    void setUp() {
        accountsRepository.deleteAll();
        accountsRepository.saveAndFlush(Account.builder()
                .accountNumber(ACCOUNT_NUMBER)
                .keycloakUserId(ownerSub)
                .customerId(UUID.randomUUID())
                .accountType(Account.AccountType.SAVINGS)
                .balance(new BigDecimal("5000.00"))
                .minimumBalance(new BigDecimal("1000.00"))
                .currency("INR")
                .status(Account.AccountStatus.ACTIVE)
                .branchCode("BR001")
                .ifscCode("RWAB001")
                .build());
    }

    /** JWT with the given subject + realm roles (authorities for the filter chain,
     *  realm_access claim for AccountAccessPolicy's privileged check). */
    private static RequestPostProcessor jwtWith(String subject, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        return jwt()
                .jwt(j -> j.subject(subject).claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(authorities);
    }

    @Test
    void getBalance_Returns200_WhenOwner() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{n}/balance", ACCOUNT_NUMBER)
                        .with(jwtWith(ownerSub, "CUSTOMER")))
                .andExpect(status().isOk());
    }

    @Test
    void getBalance_Returns404_WhenOtherCustomer() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{n}/balance", ACCOUNT_NUMBER)
                        .with(jwtWith(otherSub, "CUSTOMER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBalance_Returns200_WhenStaff() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{n}/balance", ACCOUNT_NUMBER)
                        .with(jwtWith("staff-sub-0003", "BRANCH_MANAGER")))
                .andExpect(status().isOk());
    }

    @Test
    void getBalance_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{n}/balance", ACCOUNT_NUMBER))
                .andExpect(status().isUnauthorized());
    }
}