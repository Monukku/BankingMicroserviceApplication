package com.rewabank.transactions;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the HikariCP max-lifetime in application.yml (read from the file so a test profile
 * can't mask it). Neon's serverless pooler drops idle connections server-side (~5 min), so
 * Hikari must recycle connections before that or load hits stale "connection has been
 * closed" validation failures.
 */
class DatasourceConfigTest {

    @Test
    void hikariMaxLifetime_recyclesBeforeNeonIdleCutoff() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        long maxLifetime = Long.parseLong(yaml.getObject().getProperty("spring.datasource.hikari.max-lifetime"));
        assertThat(maxLifetime).isPositive().isLessThanOrEqualTo(300_000L);
    }
}