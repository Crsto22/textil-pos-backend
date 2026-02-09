package com.sistemapos.sistematextil.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.services.AuthenticationService;
import com.sistemapos.sistematextil.util.AuthenticationRequest;
import com.sistemapos.sistematextil.util.AuthenticationResponse;
import com.sistemapos.sistematextil.util.RegisterRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("api/auth")
@RequiredArgsConstructor
@Validated
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    @PostMapping("/registro")
    public ResponseEntity<String> registrar(@Valid @RequestBody RegisterRequest request) {
        try {
            String mensaje = authenticationService.register(request);
            return ResponseEntity.ok(mensaje);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error en el registro: " + e.getMessage());
        }
    }

    @PostMapping("/autenticarse")
    public ResponseEntity<?> authenticar(@Valid @RequestBody AuthenticationRequest request) {
        try {
            AuthenticationResponse response = authenticationService.authenticate(request);
            return ResponseEntity.ok(response);
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Correo o contraseña incorrectos");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al autenticar: " + e.getMessage());
        }
    }

}
