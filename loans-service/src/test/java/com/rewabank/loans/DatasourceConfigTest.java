package com.rewabank.loans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the HikariCP connection-timeout in application.yml (read from the file so a test
 * profile can't mask it): it must be well inside the service's 10s Istio Backend VS so the
 * service fails internally before Istio cuts the request.
 */
class DatasourceConfigTest {

    @Test
    void hikariConnectionTimeout_isWellUnderBackendVs() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        assertThat(yaml.getObject().getProperty("spring.datasource.hikari.connection-timeout"))
                .isEqualTo("3000");
    }

    @Test
    void hikariMaxLifetime_recyclesBeforeNeonIdleCutoff() {
        // Neon's serverless pooler drops idle connections server-side (~5 min). Hikari must
        // recycle before that or load hits stale "connection has been closed" failures.
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        long maxLifetime = Long.parseLong(yaml.getObject().getProperty("spring.datasource.hikari.max-lifetime"));
        assertThat(maxLifetime).isPositive().isLessThanOrEqualTo(300_000L);
    }
}