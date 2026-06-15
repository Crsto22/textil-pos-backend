package com.sistemapos.sistematextil.controllers;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.services.SucursalMetodoPagoConfigService;
import com.sistemapos.sistematextil.util.metodopago.SucursalMetodoPagoConfigUpdateRequest;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(value = "api/config/sucursales/{idSucursal}/metodos-pago", produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class SucursalMetodoPagoConfigController {

    private final SucursalMetodoPagoConfigService sucursalMetodoPagoConfigService;

    @GetMapping
    public ResponseEntity<?> listar(@PathVariable Integer idSucursal) {
        try {
            return ResponseEntity.ok(sucursalMetodoPagoConfigService.listarPorSucursal(idSucursal));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al listar metodos de pago de la sucursal" : e.getMessage();
            HttpStatus status = message.toLowerCase().contains("no encontrada")
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @PutMapping
    public ResponseEntity<?> actualizar(
            @PathVariable Integer idSucursal,
            @Valid @RequestBody SucursalMetodoPagoConfigUpdateRequest request) {
        try {
            return ResponseEntity.ok(sucursalMetodoPagoConfigService.actualizarPorSucursal(idSucursal, request));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null
                    ? "Error al actualizar metodos de pago de la sucursal"
                    : e.getMessage();
            HttpStatus status = message.toLowerCase().contains("no encontrad")
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(Map.of("message", message));
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
}
