package com.rewabank.cards.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.cards.client.AccountsFeignClient;
import com.rewabank.cards.entity.Card;
import com.rewabank.cards.kafka.FraudEventConsumer;
import com.rewabank.cards.repository.CardRepository;
import com.rewabank.cards.service.CardTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
        // Basic context loading test
        assertNotNull(mockMvc);
        assertNotNull(cardRepository);
        assertNotNull(savedCard);
    }
}
