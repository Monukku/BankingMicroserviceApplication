package com.rewabank.cards.service;

import com.rewabank.cards.repository.CardRepository;
import com.rewabank.cards.util.EncryptionUtil;
import com.rewabank.cards.kafka.CardEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private CardEventProducer eventProducer;

    @Mock
    private EncryptionUtil encryptionUtil;

    private CardService cardService;

    @BeforeEach
    void setUp() {
        cardService = new CardService(cardRepository, eventProducer, encryptionUtil);
    }

    @Test
    void contextLoads() {
        // Test that CardService initializes correctly with mocked dependencies
        assertNotNull(cardService);
    }
}
