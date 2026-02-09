package com.sistemapos.sistematextil.controllers;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.config.CookieUtil;
import com.sistemapos.sistematextil.services.AuthenticationService;
import com.sistemapos.sistematextil.services.AuthenticationService.LoginResult;
import com.sistemapos.sistematextil.services.AuthenticationService.RefreshResult;
import com.sistemapos.sistematextil.util.AuthenticationRequest;
import com.sistemapos.sistematextil.util.RegisterRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("api/auth")
@RequiredArgsConstructor
@Validated
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final CookieUtil cookieUtil;

    // ── POST /api/auth/registro ──
    @PostMapping("/registro")
    public ResponseEntity<?> registrar(@Valid @RequestBody RegisterRequest request) {
        try {
            String mensaje = authenticationService.register(request);
            return ResponseEntity.ok(Map.of("message", mensaje));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Error en el registro: " + e.getMessage()));
        }
    }

    // ── POST /api/auth/autenticarse ──
    // Body JSON: { "email": "...", "password": "..." }
    // Respuesta 200: Set-Cookie refresh_token (HttpOnly) + body con access_token y datos del usuario
    // Respuesta 401: { "message": "Correo o contraseña incorrectos" }
    @PostMapping("/autenticarse")
    public ResponseEntity<?> autenticarse(@Valid @RequestBody AuthenticationRequest request) {
        try {
            LoginResult result = authenticationService.authenticate(request);

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookieUtil.createRefreshTokenCookie(result.refreshToken()).toString())
                    .body(result.response());

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Correo o contraseña incorrectos"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error al autenticar: " + e.getMessage()));
        }
    }

    // ── POST /api/auth/refresh ──
    // Sin body. Lee refresh_token desde cookie HttpOnly.
    // Respuesta 200: nuevo access_token + rota refresh_token (nueva cookie)
    // Respuesta 401: { "message": "Refresh token inválido o expirado" }
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken) {
        try {
            if (refreshToken == null || refreshToken.isBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Refresh token no proporcionado"));
            }

            RefreshResult result = authenticationService.refresh(refreshToken);

            // Rotar cookie con el nuevo refresh token
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookieUtil.createRefreshTokenCookie(result.refreshToken()).toString())
                    .body(Map.of("access_token", result.accessToken()));

        } catch (Exception e) {
            // Si falla, borrar cookie corrupta
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, cookieUtil.deleteRefreshTokenCookie().toString())
                    .body(Map.of("message", "Refresh token inválido o expirado"));
        }
    }

    // ── POST /api/auth/logout ──
    // Sin body. Borra cookie refresh_token.
    // Respuesta 200: { "ok": true }
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtil.deleteRefreshTokenCookie().toString())
                .body(Map.of("ok", true));
    }
}
