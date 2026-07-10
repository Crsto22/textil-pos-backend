package com.sistemapos.sistematextil.controllers;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.services.EcommercePromocionComboService;
import com.sistemapos.sistematextil.util.ecommerce.EcommercePromocionComboEstadoRequest;
import com.sistemapos.sistematextil.util.ecommerce.EcommercePromocionComboRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("api/ecommerce/promociones-combo")
@RequiredArgsConstructor
public class EcommercePromocionComboController {

    private final EcommercePromocionComboService service;

    @GetMapping
    public ResponseEntity<?> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String vigencia,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(service.listar(page, vigencia, correo(authentication)));
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PostMapping
    public ResponseEntity<?> crear(
            @Valid @RequestBody EcommercePromocionComboRequest request,
            Authentication authentication) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request, correo(authentication)));
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PutMapping("{id}")
    public ResponseEntity<?> actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody EcommercePromocionComboRequest request,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(service.actualizar(id, request, correo(authentication)));
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PatchMapping("{id}/estado")
    public ResponseEntity<?> actualizarEstado(
            @PathVariable Integer id,
            @Valid @RequestBody EcommercePromocionComboEstadoRequest request,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(service.actualizarEstado(id, request, correo(authentication)));
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @DeleteMapping("{id}")
    public ResponseEntity<?> eliminar(@PathVariable Integer id, Authentication authentication) {
        try {
            service.eliminar(id, correo(authentication));
            return ResponseEntity.ok(Map.of("message", "Promocion combo eliminada"));
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage() != null
                        ? fieldError.getDefaultMessage()
                        : "Datos de entrada invalidos")
                .orElse("Datos de entrada invalidos");
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    private String correo(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return authentication.getName();
    }

    private ResponseEntity<Map<String, String>> error(RuntimeException e) {
        HttpStatus status = e instanceof AccessDeniedException ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Map.of("message", e.getMessage() == null ? "No se pudo procesar la solicitud" : e.getMessage()));
    }
}
