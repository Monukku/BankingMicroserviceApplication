package com.rewabank.transactions;

import com.rewabank.transactions.client.AccountsFeignClient;
import com.rewabank.transactions.client.FraudFeignClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TransactionServiceApplicationTests {

    // Feign clients — no real services in test environment
    @SuppressWarnings("deprecation") @MockBean AccountsFeignClient    accountsFeignClient;
    @SuppressWarnings("deprecation") @MockBean FraudFeignClient       fraudFeignClient;
    // Redis — excluded from autoconfigure but RedisConfig needs the factory
    @SuppressWarnings("deprecation") @MockBean RedisConnectionFactory redisConnectionFactory;

    @Test
    void contextLoads() {
    }
}
