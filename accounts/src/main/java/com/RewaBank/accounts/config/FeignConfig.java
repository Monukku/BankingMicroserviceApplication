package com.rewabank.accounts.config;

import feign.Logger;
import feign.Request;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class FeignConfig {

    // KYC gate call to Customers MS — 5s timeout
    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(
                3, TimeUnit.SECONDS,   // connect timeout
                5, TimeUnit.SECONDS,   // read timeout
                true
        );
    }

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
