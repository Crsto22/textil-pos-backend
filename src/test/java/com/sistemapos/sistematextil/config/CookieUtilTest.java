package com.sistemapos.sistematextil.config;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CookieUtilTest {

    @Test
    void rechazaSameSiteNoneSinCookieSegura() {
        JwtConfig config = new JwtConfig();
        config.setCookieSameSite("None");
        config.setCookieSecure(false);

        assertThrows(IllegalStateException.class, () -> new CookieUtil(config).validarConfiguracion());
    }

    @Test
    void rechazaDominioConProtocolo() {
        JwtConfig config = new JwtConfig();
        config.setCookieSecure(true);
        config.setCookieSameSite("None");
        config.setCookieDomain("https://kiments.com.pe");

        assertThrows(IllegalStateException.class, () -> new CookieUtil(config).validarConfiguracion());
    }
}
