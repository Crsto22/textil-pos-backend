package com.sistemapos.sistematextil.services.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiSecretEncryptionService {

    private static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec key;

    public AiSecretEncryptionService(@Value("${crm.whatsapp.ai.secrets.master-key:}") String masterKey) {
        this.key = createKey(masterKey == null ? "" : masterKey.trim());
    }

    public boolean isConfigured() {
        return key != null;
    }

    public EncryptedSecret encrypt(String plainText, Long connectionId) {
        requireConfigured();
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(connectionId));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return new EncryptedSecret(
                    Base64.getEncoder().encodeToString(encrypted),
                    Base64.getEncoder().encodeToString(nonce));
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo cifrar la credencial de IA", error);
        }
    }

    public String decrypt(String ciphertext, String nonce, Long connectionId) {
        requireConfigured();
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_BITS, Base64.getDecoder().decode(nonce)));
            cipher.updateAAD(aad(connectionId));
            return new String(cipher.doFinal(Base64.getDecoder().decode(ciphertext)), StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo descifrar la credencial de IA", error);
        }
    }

    private SecretKeySpec createKey(String value) {
        if (value.isBlank()) return null;
        byte[] material = decodeBase64Key(value);
        if (material == null) {
            if (value.length() < 32) {
                throw new IllegalStateException("AI_SECRETS_MASTER_KEY debe tener al menos 32 caracteres");
            }
            try {
                material = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            } catch (Exception error) {
                throw new IllegalStateException("No se pudo preparar AI_SECRETS_MASTER_KEY", error);
            }
        }
        return new SecretKeySpec(material, "AES");
    }

    private byte[] decodeBase64Key(String value) {
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            return decoded.length == 32 ? decoded : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private byte[] aad(Long connectionId) {
        return ("crm-whatsapp-ai:" + connectionId).getBytes(StandardCharsets.UTF_8);
    }

    private void requireConfigured() {
        if (key == null) {
            throw new IllegalStateException("AI_SECRETS_MASTER_KEY no esta configurada");
        }
    }

    public record EncryptedSecret(String ciphertext, String nonce) {}
}
