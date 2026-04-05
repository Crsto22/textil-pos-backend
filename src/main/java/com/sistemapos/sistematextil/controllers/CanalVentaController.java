package com.sistemapos.sistematextil.controllers;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.services.CanalVentaService;
import com.sistemapos.sistematextil.util.canalventa.CanalVentaCreateRequest;
import com.sistemapos.sistematextil.util.canalventa.CanalVentaUpdateRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(value = "api/canal-venta", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class CanalVentaController {

    private final CanalVentaService service;

    @GetMapping("listar")
    public ResponseEntity<?> listar(
            Authentication authentication,
            @RequestParam(name = "idSucursal", required = false) Integer idSucursal) {
        try {
            return ResponseEntity.ok(service.listar(idSucursal, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al listar canales de venta" : e.getMessage();
            return ResponseEntity.status(resolverStatus(message)).body(Map.of("message", message));
        }
    }

    @PostMapping("insertar")
    public ResponseEntity<?> insertar(
            Authentication authentication,
            @Valid @RequestBody CanalVentaCreateRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(service.insertar(request, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al crear canal de venta" : e.getMessage();
            return ResponseEntity.status(resolverStatus(message)).body(Map.of("message", message));
        }
    }

    @PutMapping("actualizar/{id}")
    public ResponseEntity<?> actualizar(
            Authentication authentication,
            @PathVariable Integer id,
            @Valid @RequestBody CanalVentaUpdateRequest request) {
        try {
            return ResponseEntity.ok(service.actualizar(id, request, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al actualizar canal de venta" : e.getMessage();
            return ResponseEntity.status(resolverStatus(message)).body(Map.of("message", message));
        }
    }

    @DeleteMapping("eliminar/{id}")
    public ResponseEntity<?> eliminar(Authentication authentication, @PathVariable Integer id) {
        try {
            service.eliminar(id, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(Map.of("message", "Canal de venta eliminado logicamente"));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al eliminar canal de venta" : e.getMessage();
            return ResponseEntity.status(resolverStatus(message)).body(Map.of("message", message));
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

    private HttpStatus resolverStatus(String message) {
        String normalized = message.toLowerCase();
        if (normalized.contains("no autenticado") || normalized.contains("no tiene permisos")) {
            return HttpStatus.FORBIDDEN;
        }
        if (normalized.contains("no encontrado") || normalized.contains("no encontrada")) {
            return HttpStatus.NOT_FOUND;
        }
        if (normalized.contains("ya existe")) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.BAD_REQUEST;
    }

    private String obtenerCorreoAutenticado(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return authentication.getName();
    }
}
