package com.sistemapos.sistematextil.services.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AiSecretEncryptionServiceTest {

    private final AiSecretEncryptionService service = new AiSecretEncryptionService(
            "clave-maestra-local-de-pruebas-1234567890");

    @Test
    void cifraYDescifraLaClaveParaLaMismaConexion() {
        var encrypted = service.encrypt("AIza-clave-secreta-de-prueba", 10L);

        assertNotEquals("AIza-clave-secreta-de-prueba", encrypted.ciphertext());
        assertEquals(
                "AIza-clave-secreta-de-prueba",
                service.decrypt(encrypted.ciphertext(), encrypted.nonce(), 10L));
    }

    @Test
    void rechazaDescifradoDesdeOtraConexion() {
        var encrypted = service.encrypt("AIza-clave-secreta-de-prueba", 10L);

        assertThrows(
                IllegalStateException.class,
                () -> service.decrypt(encrypted.ciphertext(), encrypted.nonce(), 11L));
    }
}
