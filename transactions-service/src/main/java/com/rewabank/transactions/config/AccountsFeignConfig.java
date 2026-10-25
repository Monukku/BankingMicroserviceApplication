package com.rewabank.transactions.config;

import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import java.util.concurrent.TimeUnit;

public class AccountsFeignConfig {

    @Bean
    public Request.Options accountsRequestOptions() {
        return new Request.Options(
                2, TimeUnit.SECONDS,
                6, TimeUnit.SECONDS,   // slightly above Resilience4j TimeLimiter (5s) so CB fires first
                true
        );
    }

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public FeignJwtInterceptor feignJwtInterceptor() {
        return new FeignJwtInterceptor();
    }

    @Bean
    public RequestInterceptor internalCallInterceptor(
            @Value("${internal.call.secret}") String secret) {
        return template -> template.header("X-Internal-Call", secret);
    }
}