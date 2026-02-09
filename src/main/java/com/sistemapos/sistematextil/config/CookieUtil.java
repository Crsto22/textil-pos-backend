package com.sistemapos.sistematextil.config;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CookieUtil {

    private static final String COOKIE_NAME = "refresh_token";
    private static final String COOKIE_PATH = "/api/auth";

    private final JwtConfig jwtConfig;

    // Crear cookie con refresh token
    public ResponseCookie createRefreshTokenCookie(String refreshToken) {
        return ResponseCookie.from(COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(Boolean.TRUE.equals(jwtConfig.getCookieSecure()))
                .path(COOKIE_PATH)
                .maxAge(jwtConfig.getRefreshTokenExpirationInSeconds())
                .sameSite("Lax")
                .build();
    }

    // Borrar cookie (logout)
    public ResponseCookie deleteRefreshTokenCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(Boolean.TRUE.equals(jwtConfig.getCookieSecure()))
                .path(COOKIE_PATH)
                .maxAge(0)
                .sameSite("Lax")
                .build();
    }
}
