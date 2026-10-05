package com.sistemapos.sistematextil.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void creaCookieCompartidaEntreSubdominios() {
        JwtConfig config = new JwtConfig();
        config.setCookieSecure(true);
        config.setCookieSameSite("Lax");
        config.setCookieDomain(".kiments.tech");
        config.setRefreshTokenExpirationDays(7);

        var cookie = new CookieUtil(config).createRefreshTokenCookie("token");

        assertEquals(".kiments.tech", cookie.getDomain());
        assertEquals("/api/auth", cookie.getPath());
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.isSecure());
    }
}
