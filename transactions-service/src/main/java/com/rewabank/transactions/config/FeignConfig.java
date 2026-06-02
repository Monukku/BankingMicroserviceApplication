package com.rewabank.transactions.config;

import feign.Logger;
import feign.Request;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.TimeUnit;

@Configuration
public class FeignConfig {

    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(
                2, TimeUnit.SECONDS,   // connect timeout
                3, TimeUnit.SECONDS,   // read timeout — fraud must answer in 3s
                true
        );
    }

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
