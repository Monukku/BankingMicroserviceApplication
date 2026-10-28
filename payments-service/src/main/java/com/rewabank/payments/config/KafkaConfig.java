package com.rewabank.payments.config;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ── Transport security (managed/cloud Kafka) ─────────────────────────────
    // Defaults keep in-cluster/dev/test on PLAINTEXT (no-op). For a managed broker
    // set via K8s Secret: KAFKA_SECURITY_PROTOCOL=SASL_SSL,
    // KAFKA_SASL_MECHANISM=SCRAM-SHA-512, KAFKA_SASL_JAAS_CONFIG=...,
    // KAFKA_SSL_TRUSTSTORE_* only if the provider uses a private CA.
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

    private void applySecurity(Map<String, Object> props) {
        if (securityProtocol == null || securityProtocol.isBlank()
                || "PLAINTEXT".equalsIgnoreCase(securityProtocol)) {
            return;
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
    }

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        applySecurity(props);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}