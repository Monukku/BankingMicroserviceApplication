package com.rewabank.cards.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.cards.client.AccountsFeignClient;
import com.rewabank.cards.entity.Card;
import com.rewabank.cards.kafka.CardEventProducer;
import com.rewabank.cards.kafka.FraudEventConsumer;
import com.rewabank.cards.repository.CardRepository;
import com.rewabank.cards.service.CardTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
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
class CardControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CardRepository cardRepository;

    @MockitoBean
    private AccountsFeignClient accountsFeignClient;

    @MockitoBean
    private CardTransactionService transactionService;

    @MockitoBean
    private FraudEventConsumer fraudEventConsumer;

    @MockitoBean
    private CardEventProducer cardEventProducer;

    private Card savedCard;
    private String keycloakUserId;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        keycloakUserId = "test-user-" + UUID.randomUUID().toString();
        accountId = UUID.randomUUID();

        cardRepository.deleteAll();
        cardRepository.flush();

        Card card = Card.builder()
                .keycloakUserId(keycloakUserId)
                .customerId(UUID.randomUUID())
                .accountId(accountId)
                .cardNumberEncrypted("encrypted-test-card-number")
                .cardLastFour("1234")
                .cvvHash("hashed-test-cvv")
                .cardType(Card.CardType.DEBIT)
                .cardNetwork(Card.CardNetwork.VISA)
                .nameOnCard("TEST CARDHOLDER")
                .expiryDate(LocalDate.now().plusYears(5))
                .status(Card.CardStatus.ACTIVE)
                .build();

        savedCard = cardRepository.saveAndFlush(card);

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", keycloakUserId)
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    void contextLoads() {
        assertNotNull(mockMvc);
        assertNotNull(cardRepository);
        assertNotNull(savedCard);
    }

    // ── GET /api/v1/cards/my-cards ────────────────────────────────────────────

    @Test
    void getMyCards_Returns200_WithSavedCard() throws Exception {
        mockMvc.perform(get("/api/v1/cards/my-cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cardLastFour").value("1234"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    // ── GET /api/v1/cards/{id} ────────────────────────────────────────────────

    @Test
    void getCardById_Returns200_WhenOwnerRequests() throws Exception {
        mockMvc.perform(get("/api/v1/cards/{id}", savedCard.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardLastFour").value("1234"));
    }

    @Test
    void getCardById_Returns404_WhenCardDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/cards/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /api/v1/cards/{id}/block — Staff (TELLER) bypasses OTP ─────────

    @Test
    void blockCard_Returns200_WhenStaffRequests() throws Exception {
        // Re-authenticate as TELLER so isStaff=true, no OTP required
        Jwt staffJwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", keycloakUserId)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(staffJwt,
                        List.of(new SimpleGrantedAuthority("ROLE_TELLER"))));

        mockMvc.perform(patch("/api/v1/cards/{id}/block", savedCard.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Suspected fraud\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));
    }

    // ── PATCH /api/v1/cards/{id}/unblock ─────────────────────────────────────

    @Test
    void unblockCard_Returns200_WhenStaffUnblocks() throws Exception {
        // Block it first as staff
        savedCard.setStatus(Card.CardStatus.BLOCKED);
        cardRepository.saveAndFlush(savedCard);

        Jwt staffJwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", keycloakUserId)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(staffJwt,
                        List.of(new SimpleGrantedAuthority("ROLE_TELLER"))));

        mockMvc.perform(patch("/api/v1/cards/{id}/unblock", savedCard.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // ── PATCH /api/v1/cards/{id}/activate — TELLER only ─────────────────────

    @Test
    void activateCard_Returns403_WhenCustomerRole() throws Exception {
        savedCard.setStatus(Card.CardStatus.PENDING);
        cardRepository.saveAndFlush(savedCard);

        mockMvc.perform(patch("/api/v1/cards/{id}/activate", savedCard.getId()))
                .andExpect(status().isForbidden());
    }
}
