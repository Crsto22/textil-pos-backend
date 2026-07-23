package com.sistemapos.sistematextil.config;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CorsConfigTest {

    @Test
    void rechazaComodinesConCredenciales() {
        CorsConfig config = new CorsConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", "https://kiments.tech,* ");
        ReflectionTestUtils.setField(config, "allowedMethods", "GET,POST");
        ReflectionTestUtils.setField(config, "maxAge", 3600L);

        assertThrows(IllegalStateException.class, config::corsConfigurationSource);
    }
}
