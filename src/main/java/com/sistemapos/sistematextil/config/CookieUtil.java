package com.sistemapos.sistematextil.config;

import jakarta.annotation.PostConstruct;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CookieUtil {

    private static final String COOKIE_NAME = "refresh_token";
    private static final String COOKIE_PATH = "/api/auth";

    private final JwtConfig jwtConfig;

    @PostConstruct
    void validarConfiguracion() {
        String sameSite = resolverSameSite();
        if ("None".equalsIgnoreCase(sameSite) && !Boolean.TRUE.equals(jwtConfig.getCookieSecure())) {
            throw new IllegalStateException("COOKIE_SAME_SITE=None requiere COOKIE_SECURE=true");
        }
        String domain = jwtConfig.getCookieDomain();
        if (domain != null && !domain.isBlank()
                && !domain.trim().matches("^\\.?[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$")) {
            throw new IllegalStateException("COOKIE_DOMAIN debe ser un dominio valido sin protocolo ni ruta");
        }
    }

    // Crear cookie con refresh token
    public ResponseCookie createRefreshTokenCookie(String refreshToken) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(Boolean.TRUE.equals(jwtConfig.getCookieSecure()))
                .path(COOKIE_PATH)
                .maxAge(jwtConfig.getRefreshTokenExpirationInSeconds())
                .sameSite(resolverSameSite());
        aplicarDominioSiCorresponde(builder);
        return builder.build();
    }

    // Borrar cookie (logout)
    public ResponseCookie deleteRefreshTokenCookie() {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(Boolean.TRUE.equals(jwtConfig.getCookieSecure()))
                .path(COOKIE_PATH)
                .maxAge(0)
                .sameSite(resolverSameSite());
        aplicarDominioSiCorresponde(builder);
        return builder.build();
    }

    private void aplicarDominioSiCorresponde(ResponseCookie.ResponseCookieBuilder builder) {
        String domain = jwtConfig.getCookieDomain();
        if (domain != null && !domain.isBlank()) {
            builder.domain(domain.trim());
        }
    }

    private String resolverSameSite() {
        String sameSite = jwtConfig.getCookieSameSite();
        return sameSite == null || sameSite.isBlank() ? "Lax" : sameSite.trim();
    }
}
