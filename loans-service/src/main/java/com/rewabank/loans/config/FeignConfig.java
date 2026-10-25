package com.rewabank.loans.config;

import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.TimeUnit;

@Configuration
public class FeignConfig {

    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(3, TimeUnit.SECONDS, 5, TimeUnit.SECONDS, true);
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
            @Value("${internal.call.secret:rewabank-internal-secret}") String secret) {
        return template -> template.header("X-Internal-Call", secret);
    }
}