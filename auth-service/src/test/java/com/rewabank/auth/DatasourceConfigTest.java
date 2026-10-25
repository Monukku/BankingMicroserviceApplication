package com.rewabank.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the HikariCP settings in application.yaml (read straight from the file so the H2
 * test profile can't mask the production values). The connection-timeout must sit well
 * inside the service's Istio Backend VS (10s) so auth fails internally before Istio cuts
 * the request, and the pool must be large enough not to starve under load.
 */
class DatasourceConfigTest {

    private Properties prod() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        return yaml.getObject();
    }

    @Test
    void hikariConnectionTimeout_isWellUnderTheBackendVs() {
        // 3000ms << 10s Backend VS, leaving room for the query itself.
        assertThat(prod().getProperty("spring.datasource.hikari.connection-timeout"))
                .isEqualTo("3000");
    }

    @Test
    void hikariPool_isNotStarved() {
        int poolSize = Integer.parseInt(prod().getProperty("spring.datasource.hikari.maximum-pool-size"));
        assertThat(poolSize).isGreaterThanOrEqualTo(10);
    }
}