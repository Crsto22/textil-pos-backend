package com.sistemapos.sistematextil.controllers;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.services.SucursalStockService;
import com.sistemapos.sistematextil.util.sucursalstock.MovimientoStockRequest;
import com.sistemapos.sistematextil.util.sucursalstock.SucursalStockAjusteRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(value = "api/sucursal-stock", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class SucursalStockController {

    private final SucursalStockService service;

    @GetMapping("listar")
    public ResponseEntity<?> listar(
            Authentication authentication,
            @RequestParam(name = "idSucursal") Integer idSucursal,
            @RequestParam(name = "q", required = false) String q) {
        try {
            return ResponseEntity.ok(service.listar(idSucursal, q, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al listar stock por sucursal" : e.getMessage();
            return ResponseEntity.status(resolverStatus(message)).body(Map.of("message", message));
        }
    }

    @GetMapping("buscar")
    public ResponseEntity<?> obtener(
            Authentication authentication,
            @RequestParam(name = "idSucursal") Integer idSucursal,
            @RequestParam(name = "idProductoVariante") Integer idProductoVariante) {
        try {
            return ResponseEntity.ok(service.obtener(idSucursal, idProductoVariante, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al consultar stock por sucursal" : e.getMessage();
            return ResponseEntity.status(resolverStatus(message)).body(Map.of("message", message));
        }
    }

    @PostMapping("ajustar")
    public ResponseEntity<?> ajustar(
            Authentication authentication,
            @Valid @RequestBody SucursalStockAjusteRequest request) {
        try {
            return ResponseEntity.ok(service.ajustar(request, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al ajustar stock" : e.getMessage();
            return ResponseEntity.status(resolverStatus(message)).body(Map.of("message", message));
        }
    }

    @PostMapping("movimiento")
    public ResponseEntity<?> registrarMovimiento(
            Authentication authentication,
            @Valid @RequestBody MovimientoStockRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(service.registrarMovimiento(request, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al registrar movimiento de stock" : e.getMessage();
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
        if (normalized.contains("no encontrado") || normalized.contains("no encontrada") || normalized.contains("no existe")) {
            return HttpStatus.NOT_FOUND;
        }
        if (normalized.contains("insuficiente")) {
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
