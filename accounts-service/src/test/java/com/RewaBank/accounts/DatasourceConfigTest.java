package com.rewabank.accounts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the HikariCP connection-timeout in application.yml. Accounts' Backend VS is 8s and
 * the account-create path also makes a 5s Feign call to customers, so the DB-connection
 * wait must be small (2s): 2s conn + 5s Feign = 7s < 8s VS.
 */
class DatasourceConfigTest {

    @Test
    void hikariConnectionTimeout_fitsTheSequentialBudgetUnderTheBackendVs() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        int connMs = Integer.parseInt(yaml.getObject().getProperty("spring.datasource.hikari.connection-timeout"));
        assertThat(connMs).isLessThanOrEqualTo(2000);
    }
}