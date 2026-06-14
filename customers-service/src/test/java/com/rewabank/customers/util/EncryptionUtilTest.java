package com.rewabank.customers.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptionUtilTest {

    private EncryptionUtil encryptionUtil;

    @BeforeEach
    void setUp() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        encryptionUtil = new EncryptionUtil(keyGen.generateKey());
    }

    // ── encrypt / decrypt ─────────────────────────────────────────────────────

    @Test
    void encrypt_null_returnsNull() {
        assertThat(encryptionUtil.encrypt(null)).isNull();
    }

    @Test
    void decrypt_null_returnsNull() {
        assertThat(encryptionUtil.decrypt(null)).isNull();
    }

    @Test
    void encryptDecrypt_roundTrip_restoresPlaintext() {
        String plain = "234567890123";
        String ciphertext = encryptionUtil.encrypt(plain);
        assertThat(ciphertext).isNotNull().isNotEqualTo(plain);
        assertThat(encryptionUtil.decrypt(ciphertext)).isEqualTo(plain);
    }

    @Test
    void encryptDecrypt_emptyString_roundTrips() {
        String ciphertext = encryptionUtil.encrypt("");
        assertThat(encryptionUtil.decrypt(ciphertext)).isEmpty();
    }

    @Test
    void encrypt_randomIv_differentCiphertextEachCall() {
        assertThat(encryptionUtil.encrypt("same")).isNotEqualTo(encryptionUtil.encrypt("same"));
    }

    // ── maskAadhaar ───────────────────────────────────────────────────────────

    @Test
    void maskAadhaar_null_returnsPlaceholder() {
        assertThat(encryptionUtil.maskAadhaar(null)).isEqualTo("XXXX-XXXX-XXXX");
    }

    @Test
    void maskAadhaar_threeChars_returnsPlaceholder() {
        assertThat(encryptionUtil.maskAadhaar("123")).isEqualTo("XXXX-XXXX-XXXX");
    }

    @Test
    void maskAadhaar_exactlyFourChars_masksAndShowsLastFour() {
        // length 4 is NOT < 4 — exercises the non-placeholder path (kills ConditionalsBoundary)
        assertThat(encryptionUtil.maskAadhaar("1234")).isEqualTo("XXXX-XXXX-1234");
    }

    @Test
    void maskAadhaar_twelveDigits_showsLastFourOnly() {
        assertThat(encryptionUtil.maskAadhaar("234567890123")).isEqualTo("XXXX-XXXX-0123");
    }

    // ── maskPan ───────────────────────────────────────────────────────────────

    @Test
    void maskPan_null_returnsPlaceholder() {
        assertThat(encryptionUtil.maskPan(null)).isEqualTo("XXXXXXXXXXX");
    }

    @Test
    void maskPan_fourChars_returnsPlaceholder() {
        assertThat(encryptionUtil.maskPan("ABCD")).isEqualTo("XXXXXXXXXXX");
    }

    @Test
    void maskPan_tenChars_masksMiddleFive() {
        // "ABCDE1234F" → "ABC" + "XXXXX" + "4F"
        assertThat(encryptionUtil.maskPan("ABCDE1234F")).isEqualTo("ABCXXXXX4F");
    }
}