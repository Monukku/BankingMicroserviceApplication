package com.rewabank.loans.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.loans.client.AccountsFeignClient;
import com.rewabank.loans.client.FraudFeignClient;
import com.rewabank.loans.repository.LoanApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "build.version=1.0.0"
})
class LoanControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoanApplicationRepository loanRepository;

    @MockBean
    private AccountsFeignClient accountsFeignClient;

    @MockBean
    private FraudFeignClient fraudFeignClient;

    private UUID customerId;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        customerId = UUID.randomUUID();
        accountId = UUID.randomUUID();

        loanRepository.deleteAll();
        loanRepository.flush();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "test-user-" + customerId)
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    void contextLoads() {
        assertNotNull(mockMvc);
        assertNotNull(loanRepository);
    }

    // ── GET /api/v1/loans/my-loans ────────────────────────────────────────────

    @Test
    void getMyLoans_Returns200_WithEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/loans/my-loans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── GET /api/v1/loans/{id} ────────────────────────────────────────────────

    @Test
    void getById_Returns404_WhenLoanNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/loans/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /api/v1/loans/{id}/review/start — staff only ───────────────────

    @Test
    void startReview_Returns403_WhenCustomerRole() throws Exception {
        mockMvc.perform(patch("/api/v1/loans/{id}/review/start", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/v1/loans — staff only ───────────────────────────────────────

    @Test
    void getAllLoans_Returns403_WhenCustomerRole() throws Exception {
        mockMvc.perform(get("/api/v1/loans"))
                .andExpect(status().isForbidden());
    }

    // ── PATCH /api/v1/loans/{id}/disburse — staff only ───────────────────────

    @Test
    void disburse_Returns403_WhenCustomerRole() throws Exception {
        mockMvc.perform(patch("/api/v1/loans/{id}/disburse", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }
}
