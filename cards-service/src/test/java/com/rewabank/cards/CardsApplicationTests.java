package com.rewabank.cards;

import com.rewabank.cards.service.CardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
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
class CardsApplicationTests {

    @Autowired
    private CardService cardService;

    @Test
    void contextLoads() {
        // Test that the application context loads successfully
        assert cardService != null;
    }
}
