package com.sistemapos.sistematextil.services;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.config.SunatProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SunatSecretCryptoService {

    private static final String PREFIX = "ENC:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    private final SunatProperties sunatProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, buildKey(), new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(plainText.trim().getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);

            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new RuntimeException("No se pudo cifrar la configuracion SUNAT");
        }
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isBlank()) {
            return null;
        }
        if (!encryptedText.startsWith(PREFIX)) {
            return encryptedText;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(encryptedText.substring(PREFIX.length()));
            ByteBuffer buffer = ByteBuffer.wrap(payload);

            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, buildKey(), new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo descifrar la configuracion SUNAT");
        }
    }

    private SecretKeySpec buildKey() throws Exception {
        String configuredKey = sunatProperties.getCryptoKey();
        if (configuredKey == null || configuredKey.isBlank()) {
            throw new RuntimeException("Configure SUNAT_CRYPTO_KEY para proteger claves SUNAT");
        }
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(configuredKey.trim().getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(Arrays.copyOf(digest, 32), "AES");
    }
}
