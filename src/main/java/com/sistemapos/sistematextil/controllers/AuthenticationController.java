package com.sistemapos.sistematextil.controllers;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sistemapos.sistematextil.config.CookieUtil;
import com.sistemapos.sistematextil.services.AuthenticationService;
import com.sistemapos.sistematextil.services.AuthenticationService.LoginResult;
import com.sistemapos.sistematextil.services.AuthenticationService.RefreshResult;
import com.sistemapos.sistematextil.util.auth.AuthenticationRequest;
import com.sistemapos.sistematextil.util.auth.ChangePasswordRequest;
import com.sistemapos.sistematextil.util.auth.RegisterRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("api/auth")
@RequiredArgsConstructor
@Validated
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final CookieUtil cookieUtil;

    @PostMapping("/registro")
    public ResponseEntity<?> registrar(@Valid @RequestBody RegisterRequest request) {
        try {
            String mensaje = authenticationService.register(request);
            return ResponseEntity.ok(Map.of("message", mensaje));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/autenticarse")
    public ResponseEntity<?> autenticarse(@Valid @RequestBody AuthenticationRequest request) {
        try {
            LoginResult result = authenticationService.authenticate(request);

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookieUtil.createRefreshTokenCookie(result.refreshToken()).toString())
                    .body(result.response());

        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Usuario inactivo"));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Correo o contrasena incorrectos"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error al autenticar: " + e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken) {
        try {
            if (refreshToken == null || refreshToken.isBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Refresh token no proporcionado"));
            }

            RefreshResult result = authenticationService.refresh(refreshToken);

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookieUtil.createRefreshTokenCookie(result.refreshToken()).toString())
                    .body(Map.of("access_token", result.accessToken()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, cookieUtil.deleteRefreshTokenCookie().toString())
                    .body(Map.of("message", "Refresh token invalido o expirado"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtil.deleteRefreshTokenCookie().toString())
                .body(Map.of("ok", true));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        try {
            return ResponseEntity.ok(authenticationService.me(obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al obtener usuario autenticado" : e.getMessage();
            return ResponseEntity.status(resolverStatus(message, HttpStatus.BAD_REQUEST))
                    .body(Map.of("message", message));
        }
    }

    @PostMapping("/cambiar-password")
    public ResponseEntity<?> cambiarPassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        try {
            String mensaje = authenticationService.changePassword(obtenerCorreoAutenticado(authentication), request);
            return ResponseEntity.ok(Map.of("message", mensaje));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al cambiar contrasena" : e.getMessage();
            return ResponseEntity.status(resolverStatus(message, HttpStatus.BAD_REQUEST))
                    .body(Map.of("message", message));
        }
    }

    @PutMapping(value = "/foto-perfil", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> actualizarFotoPerfil(
            Authentication authentication,
            @RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(authenticationService.actualizarFotoPerfil(
                    obtenerCorreoAutenticado(authentication),
                    file));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al actualizar foto de perfil" : e.getMessage();
            return ResponseEntity.status(resolverStatus(message, HttpStatus.BAD_REQUEST))
                    .body(Map.of("message", message));
        }
    }

    @DeleteMapping("/foto-perfil")
    public ResponseEntity<?> eliminarFotoPerfil(Authentication authentication) {
        try {
            return ResponseEntity.ok(authenticationService.eliminarFotoPerfil(
                    obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al eliminar foto de perfil" : e.getMessage();
            return ResponseEntity.status(resolverStatus(message, HttpStatus.BAD_REQUEST))
                    .body(Map.of("message", message));
        }
    }

    private String obtenerCorreoAutenticado(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return authentication.getName();
    }

    private HttpStatus resolverStatus(String message, HttpStatus defaultStatus) {
        String normalizedMessage = message.toLowerCase();
        if (normalizedMessage.contains("no encontrado")) {
            return HttpStatus.NOT_FOUND;
        }
        if (normalizedMessage.contains("no autenticado")) {
            return HttpStatus.UNAUTHORIZED;
        }
        return defaultStatus;
    }
}
