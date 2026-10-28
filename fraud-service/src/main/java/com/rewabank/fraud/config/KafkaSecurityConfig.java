package com.rewabank.fraud.config;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaConsumerFactoryCustomizer;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Applies transport security (SASL_SSL etc.) to the Spring Boot auto-configured
 * Kafka producer/consumer factories, only when a non-PLAINTEXT protocol is set.
 * In-cluster / dev / test stay PLAINTEXT (empty overlay = no-op). See the K8s
 * Secret env vars KAFKA_SECURITY_PROTOCOL / KAFKA_SASL_* / KAFKA_SSL_TRUSTSTORE_*.
 */
@Configuration
public class KafkaSecurityConfig {

    @Value("${KAFKA_SECURITY_PROTOCOL:PLAINTEXT}")
    private String securityProtocol;
    @Value("${KAFKA_SASL_MECHANISM:}")
    private String saslMechanism;
    @Value("${KAFKA_SASL_JAAS_CONFIG:}")
    private String saslJaasConfig;
    @Value("${KAFKA_SSL_TRUSTSTORE_LOCATION:}")
    private String sslTruststoreLocation;
    @Value("${KAFKA_SSL_TRUSTSTORE_PASSWORD:}")
    private String sslTruststorePassword;
    @Value("${KAFKA_SSL_TRUSTSTORE_TYPE:PKCS12}")
    private String sslTruststoreType;

    private Map<String, Object> securityConfigs() {
        Map<String, Object> props = new HashMap<>();
        if (securityProtocol == null || securityProtocol.isBlank()
                || "PLAINTEXT".equalsIgnoreCase(securityProtocol)) {
            return props;
        }
        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol);
        if (!saslMechanism.isBlank()) {
            props.put(SaslConfigs.SASL_MECHANISM, saslMechanism);
        }
        if (!saslJaasConfig.isBlank()) {
            props.put(SaslConfigs.SASL_JAAS_CONFIG, saslJaasConfig);
        }
        if (!sslTruststoreLocation.isBlank()) {
            props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, sslTruststoreLocation);
            props.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, sslTruststorePassword);
            props.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, sslTruststoreType);
        }
        return props;
    }

    @Bean
    public DefaultKafkaProducerFactoryCustomizer kafkaProducerSecurityCustomizer() {
        return factory -> factory.updateConfigs(securityConfigs());
    }

    @Bean
    public DefaultKafkaConsumerFactoryCustomizer kafkaConsumerSecurityCustomizer() {
        return factory -> factory.updateConfigs(securityConfigs());
    }
}