package com.rewabank.accounts.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.accounts.client.CustomersFeignClient;
import com.rewabank.accounts.dto.*;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AccountControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountsRepository accountsRepository;

    @SuppressWarnings("deprecation")
    @MockBean
    private AccountReadService accountReadService;

    @SuppressWarnings("deprecation")
    @MockBean
    private CustomersFeignClient customersFeignClient;

    @SuppressWarnings("deprecation")
    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    private Account savedAccount;
    private String keycloakUserId;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        keycloakUserId = "test-user-123";
        customerId = UUID.randomUUID();

        accountsRepository.deleteAll();
        accountsRepository.flush();

        Account account = Account.builder()
                .accountNumber("123456789012")
                .keycloakUserId(keycloakUserId)
                .customerId(customerId)
                .accountType(Account.AccountType.SAVINGS)
                .balance(BigDecimal.valueOf(1000))
                .status(Account.AccountStatus.ACTIVE)
                .branchCode("BR001")
                .ifscCode("IFSC001")
                .build();
        savedAccount = accountsRepository.saveAndFlush(account);

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", keycloakUserId)
                .claim("realm_access", Map.of("roles", List.of("TELLER", "CUSTOMER")))
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_TELLER"),
                        new SimpleGrantedAuthority("ROLE_BRANCH_MANAGER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(customersFeignClient.getKycStatus(any()))
                .thenReturn(new KycStatusResponse(
                        customerId.toString(),
                        keycloakUserId,
                        "VERIFIED",
                        true,
                        "KYC verified"
                ));
    }

    @Test
    void createAccount_ShouldReturnCreatedAccount() throws Exception {
        // Remove existing account so we can create a new one
        accountsRepository.deleteAll();
        accountsRepository.flush();

        AccountCreateRequest request = new AccountCreateRequest(Account.AccountType.SAVINGS, "BR001", "IFSC001");
        String requestJson = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/v1/accounts")
                        .header("X-Customer-Id", customerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountType").value("SAVINGS"))
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void getMyAccounts_ShouldReturnAccountsList() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/my-accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountNumber").value("123456789012"))
                .andExpect(jsonPath("$[0].balance").value(1000));
    }

    @Test
    void getAccountById_ShouldReturnAccount() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", savedAccount.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("123456789012"));
    }

    @Test
    void getBalance_ShouldReturnBalance() throws Exception {
        BalanceResponse balanceResponse = new BalanceResponse(
                "123456789012", BigDecimal.valueOf(1500), BigDecimal.valueOf(1500), "INR", "ACTIVE", LocalDateTime.now());
        when(accountReadService.getBalance("123456789012")).thenReturn(balanceResponse);

        mockMvc.perform(get("/api/v1/accounts/{accountNumber}/balance", "123456789012"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("123456789012"))
                .andExpect(jsonPath("$.balance").value(1500))
                .andExpect(jsonPath("$.currency").value("INR"));
    }

    @Test
    void activateAccount_ShouldActivateAccount() throws Exception {
        savedAccount.setStatus(Account.AccountStatus.PENDING);
        accountsRepository.save(savedAccount);

        mockMvc.perform(patch("/api/v1/accounts/{id}/activate", savedAccount.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void freezeAccount_ShouldFreezeAccount() throws Exception {
        mockMvc.perform(patch("/api/v1/accounts/{id}/freeze", savedAccount.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));
    }

    @Test
    void unfreezeAccount_ShouldUnfreezeAccount() throws Exception {
        savedAccount.setStatus(Account.AccountStatus.FROZEN);
        accountsRepository.save(savedAccount);

        mockMvc.perform(patch("/api/v1/accounts/{id}/unfreeze", savedAccount.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}