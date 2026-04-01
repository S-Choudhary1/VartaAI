package tech.vartaai.whatsappcrm.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class CredentialEncryption {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final byte[] keyBytes;

    public CredentialEncryption(
            @Value("${mcp.credentials.encryption-key:#{null}}") String encryptionKey) {
        if (encryptionKey != null && !encryptionKey.isBlank()) {
            // Use first 32 bytes of the key (AES-256)
            byte[] raw = encryptionKey.getBytes(StandardCharsets.UTF_8);
            this.keyBytes = new byte[32];
            System.arraycopy(raw, 0, keyBytes, 0, Math.min(raw.length, 32));
        } else {
            // Fallback: no encryption key configured — store plaintext
            this.keyBytes = null;
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return plaintext;
        if (keyBytes == null) return plaintext; // no encryption key configured

        try {
            SecureRandom random = new SecureRandom();
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext
            byte[] result = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, result, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, result, IV_LENGTH, encrypted.length);

            return "ENC:" + Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) return ciphertext;
        if (keyBytes == null) return ciphertext;
        if (!ciphertext.startsWith("ENC:")) return ciphertext; // not encrypted

        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertext.substring(4));

            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(decoded, 0, iv, 0, IV_LENGTH);

            byte[] encrypted = new byte[decoded.length - IV_LENGTH];
            System.arraycopy(decoded, IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
