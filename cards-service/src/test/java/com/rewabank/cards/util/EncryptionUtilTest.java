package com.rewabank.cards.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionUtilTest {

    private EncryptionUtil encryptionUtil;

    @BeforeEach
    void setUp() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        SecretKey key = keyGen.generateKey();
        encryptionUtil = new EncryptionUtil(key);
    }

    @Test
    void encrypt_Decrypt_RoundTrip() {
        String plain = "4111111111111111";
        String encrypted = encryptionUtil.encrypt(plain);

        assertThat(encrypted).isNotNull();
        assertThat(encrypted).isNotEqualTo(plain);

        String decrypted = encryptionUtil.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(plain);
    }

    @Test
    void encrypt_ShouldReturnNull_WhenInputIsNull() {
        assertThat(encryptionUtil.encrypt(null)).isNull();
    }

    @Test
    void decrypt_ShouldReturnNull_WhenInputIsNull() {
        assertThat(encryptionUtil.decrypt(null)).isNull();
    }

    @Test
    void encrypt_ShouldProduceDifferentOutputEachTime() {
        String plain = "4111111111111111";
        String enc1 = encryptionUtil.encrypt(plain);
        String enc2 = encryptionUtil.encrypt(plain);
        // GCM uses random IV — same plaintext produces different ciphertext
        assertThat(enc1).isNotEqualTo(enc2);
    }

    @Test
    void decrypt_ShouldThrow_WhenInvalidInput() {
        assertThatThrownBy(() -> encryptionUtil.decrypt("not-valid-base64!!!"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Decryption failed");
    }

    @Test
    void maskCardNumber_ShouldReturnMaskedFormat() {
        assertThat(encryptionUtil.maskCardNumber("1234"))
                .isEqualTo("**** **** **** 1234");
    }
}