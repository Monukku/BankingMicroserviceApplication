package com.rewabank.cards.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class EncryptionConfig {

    @Value("${encryption.aes-key}")
    private String aesKeyBase64;

    @Bean
    public SecretKey aesSecretKey() {
        byte[] keyBytes = Base64.getDecoder().decode(aesKeyBase64);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "AES key must be 256-bit (32 bytes)");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}