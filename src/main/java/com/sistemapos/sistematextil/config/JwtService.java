package com.sistemapos.sistematextil.config;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.model.CustomUser;
import com.sistemapos.sistematextil.model.Usuario;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtConfig jwtConfig;
    private final SecretKey secretKey;

    // ── ACCESS TOKEN (corto, 15 min) con claims personalizados ──
    public String generateAccessToken(CustomUser customUser) {
        Usuario user = customUser.getUsuario();
        Map<String, Object> claims = new HashMap<>();
        claims.put("rol", user.getRol().name());
        claims.put("idUsuario", user.getIdUsuario());
        claims.put("idSucursal", user.getSucursal() != null ? user.getSucursal().getIdSucursal() : null);
        return buildToken(claims, customUser, jwtConfig.getAccessTokenExpirationInMillis());
    }

    // ── REFRESH TOKEN (largo, 7 días) solo con subject (email) ──
    public String generateRefreshToken(CustomUser customUser) {
        return buildToken(new HashMap<>(), customUser, jwtConfig.getRefreshTokenExpirationInMillis());
    }

    private String buildToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails,
            long expirationMillis
    ) {
        return Jwts
                .builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(secretKey)
                .compact();
    }

    // Valida que el token no esté expirado y que el username coincida
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts
                .parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
