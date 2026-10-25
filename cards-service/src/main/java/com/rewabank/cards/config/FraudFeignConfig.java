package com.rewabank.cards.config;

import feign.Request;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

// No @Configuration — intentionally client-scoped, not a global Spring bean.
// Used only via @FeignClient(configuration = FraudFeignConfig.class).
// FeignJwtInterceptor is registered globally from FeignConfig @Configuration
// and continues to apply to all Feign clients including this one.
public class FraudFeignConfig {

    @Bean
    public Request.Options fraudRequestOptions() {
        // fraud-service SLA is <100ms; 1s cap prevents a slow fraud node
        // from blocking card transaction auth and thrashing the circuit breaker.
        return new Request.Options(500, TimeUnit.MILLISECONDS, 1000, TimeUnit.MILLISECONDS, true);
    }
}
