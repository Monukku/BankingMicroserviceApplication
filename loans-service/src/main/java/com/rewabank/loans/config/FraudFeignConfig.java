package com.rewabank.loans.config;

import feign.Request;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

// No @Configuration — intentionally client-scoped, not a global Spring bean.
// FeignJwtInterceptor is registered globally from FeignConfig @Configuration
// and continues to apply to all Feign clients including this one.
public class FraudFeignConfig {

    @Bean
    public Request.Options fraudRequestOptions() {
        // fraud-service SLA is <100ms; 1s cap at the HTTP layer ensures
        // Feign fails fast before the 3s Resilience4j timelimiter fires,
        // giving the circuit breaker clean timeout signals to count.
        return new Request.Options(500, TimeUnit.MILLISECONDS, 1000, TimeUnit.MILLISECONDS, true);
    }
}