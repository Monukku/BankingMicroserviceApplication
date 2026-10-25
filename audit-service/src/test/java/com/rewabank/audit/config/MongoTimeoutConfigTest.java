package com.rewabank.audit.config;

import com.mongodb.MongoClientSettings;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Mongo timeouts are bounded <= 5s (under audit's 10s Istio Backend VS) by
 * applying the customizer to a fresh settings builder — no running MongoDB required.
 */
class MongoTimeoutConfigTest {

    @Test
    void serverSelectionConnectAndSocketTimeouts_areAtMost5s() {
        MongoClientSettings.Builder builder = MongoClientSettings.builder();
        new MongoTimeoutConfig().mongoTimeoutCustomizer().customize(builder);
        MongoClientSettings settings = builder.build();

        assertThat(settings.getClusterSettings().getServerSelectionTimeout(TimeUnit.MILLISECONDS))
                .isLessThanOrEqualTo(5000);
        assertThat(settings.getSocketSettings().getConnectTimeout(TimeUnit.MILLISECONDS))
                .isLessThanOrEqualTo(5000);
        assertThat(settings.getSocketSettings().getReadTimeout(TimeUnit.MILLISECONDS))
                .isLessThanOrEqualTo(5000);
    }
}