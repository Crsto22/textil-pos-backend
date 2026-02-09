package com.sistemapos.sistematextil.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.jsonwebtoken.security.Keys;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "application.jwt")
public class JwtConfig {

    private String secretKey;
    private String tokenPrefix;
    private Integer accessTokenExpirationMinutes;   // access token en minutos (ej: 15)
    private Integer refreshTokenExpirationDays;      // refresh token en días (ej: 7)
    private Boolean cookieSecure;                    // true en prod (HTTPS), false en dev
    private String cookieDomain;                     // dominio para la cookie

    // Access token: minutos -> milisegundos
    public long getAccessTokenExpirationInMillis() {
        return accessTokenExpirationMinutes * 60L * 1000L;
    }

    // Refresh token: días -> milisegundos
    public long getRefreshTokenExpirationInMillis() {
        return refreshTokenExpirationDays * 24L * 60L * 60L * 1000L;
    }

    // Refresh token: días -> segundos (para Max-Age de la cookie)
    public int getRefreshTokenExpirationInSeconds() {
        return refreshTokenExpirationDays * 24 * 60 * 60;
    }

    @Bean
    SecretKey secretKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }
}
