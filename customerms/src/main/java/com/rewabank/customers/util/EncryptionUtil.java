package com.rewabank.customers.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;
import java.util.Base64;

@Component
@RequiredArgsConstructor
@Slf4j
public class EncryptionUtil {

    private static final String ALGORITHM      = "AES/GCM/NoPadding";
    private static final int    GCM_IV_LENGTH  = 12;   // 96-bit IV
    private static final int    GCM_TAG_LENGTH = 128;  // 128-bit auth tag

    private final SecretKey aesSecretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    // Encrypt — returns base64(iv + ciphertext)
    public String encrypt(String plainText) {
        if (plainText == null) return null;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, aesSecretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] cipherText  = cipher.doFinal(plainText.getBytes());
            byte[] ivAndCipher = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, ivAndCipher, 0, iv.length);
            System.arraycopy(cipherText, 0, ivAndCipher, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(ivAndCipher);
        } catch (Exception e) {
            log.error("Encryption failed: {}", e.getMessage());
            throw new RuntimeException("Encryption failed", e);
        }
    }

    // Decrypt — input is base64(iv + ciphertext)
    public String decrypt(String encryptedBase64) {
        if (encryptedBase64 == null) return null;
        try {
            byte[] ivAndCipher = Base64.getDecoder().decode(encryptedBase64);
            byte[] iv         = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[ivAndCipher.length - GCM_IV_LENGTH];

            System.arraycopy(ivAndCipher, 0, iv, 0, iv.length);
            System.arraycopy(ivAndCipher, iv.length, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, aesSecretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            return new String(cipher.doFinal(cipherText));
        } catch (Exception e) {
            log.error("Decryption failed: {}", e.getMessage());
            throw new RuntimeException("Decryption failed", e);
        }
    }

    // Returns masked Aadhaar: XXXX-XXXX-1234
    public String maskAadhaar(String aadhaar) {
        if (aadhaar == null || aadhaar.length() < 4) return "XXXX-XXXX-XXXX";
        return "XXXX-XXXX-" + aadhaar.substring(aadhaar.length() - 4);
    }

    // Returns masked PAN: ABCXXXX1234X
    public String maskPan(String pan) {
        if (pan == null || pan.length() < 5) return "XXXXXXXXXXX";
        return pan.substring(0, 3) + "XXXXX" + pan.substring(8);
    }
}
