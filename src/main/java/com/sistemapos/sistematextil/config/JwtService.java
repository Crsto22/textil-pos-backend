package com.sistemapos.sistematextil.config;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;


import javax.crypto.SecretKey;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtConfig jwtConfig;
    private final SecretKey secretKey;

    // el token NO se genera directamente desde la entidad Usuario, sino desde el objeto CustomUser, que envuelve al Usuario. ya que spring security esta  diseñado solo para trabajar con la interfaz UserDetails

    // Generar un token de acceso
    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(),userDetails);       
    }

    // Se agrega datos extras al token como roles , permisos , el generateToken de
    // arriva llama internamente a este metodo
    public String generateToken(Map<String, Object> extraClaims,
            UserDetails userDetails) {
        return buildToken(extraClaims, userDetails, jwtConfig.getTokenExpirationInMillis());
    }

    // Con este metodo generamos un token de refrezco (Refresh token)
    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(new HashMap<>(), userDetails, jwtConfig.getRefreshTokenExpirationInMillis());
    }
    
    // Esta es la parte importante y esque en aca se genera y contruye el token
    private String buildToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails,
            long expirationMillis // Milisegundos
    ) {
        return Jwts
                .builder() // Objeto con el que se armara el token
                .claims(extraClaims) // Inserta datos extras personalizado como roles
                .subject(userDetails.getUsername()) // el sujeto del token tipicamente el username de CustomUser, linea 28
                .issuedAt(new Date(System.currentTimeMillis())) // Cuando se emite el token
                .expiration(new Date(System.currentTimeMillis() + expirationMillis)) // Cuando expira
                .signWith(secretKey) // la firma digital
                .compact(); // Convierte todo a un String JWT codificado (tres partes separadas por .).
    }


    // Valida que el token no este expirado y que el username coincida
    public boolean isTookenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    // Extrae el nombre de usuario
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // Metodo generico para extraer cualquier claim
    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // Extrae todos los claim , decodifica y verifica la firma del token (claims es
    // para, ver quien eres , que permisos tienes y mas)
    private Claims extractAllClaims(String token) {
        return Jwts
                .parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Esto me mostrara en consola cuando se genera el token
    public void printTokenDates(String token) {
        Claims claims = extractAllClaims(token);
        System.out.println("Token creado: " + claims.getIssuedAt());
        System.out.println("Token expira: " + claims.getExpiration());
        System.out.println("Fecha actual" + new Date());
        System.out.println("Milisegundos restantes: " +
                (claims.getExpiration().getTime() - System.currentTimeMillis()));
    }

}
