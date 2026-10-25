package com.rewabank.audit.config;

import com.mongodb.MongoClientSettings;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Bounds MongoDB timeouts below audit's Istio Backend VS (10s) so a slow/unreachable Mongo
 * fails internally before Istio cuts the request. The connection string comes from an env
 * var (MONGODB_URI), so these can't be URI query params — they're applied here instead.
 */
@Configuration
public class MongoTimeoutConfig {

    static final int TIMEOUT_MS = 5000; // <= 5s, well under the 10s Backend VS

    @Bean
    public MongoClientSettingsBuilderCustomizer mongoTimeoutCustomizer() {
        return (MongoClientSettings.Builder builder) -> builder
                .applyToClusterSettings(c -> c.serverSelectionTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .applyToSocketSettings(s -> s
                        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }
}