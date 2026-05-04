package com.sistemapos.sistematextil.controllers;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;

import com.sistemapos.sistematextil.services.MetodoPagoConfigService;
import com.sistemapos.sistematextil.util.metodopago.MetodoPagoConfigCreateRequest;
import com.sistemapos.sistematextil.util.metodopago.MetodoPagoEstadoUpdateRequest;
import com.sistemapos.sistematextil.util.metodopago.MetodoPagoConfigUpdateRequest;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(value = "api/config/metodos-pago", produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class MetodoPagoConfigController {

    private final MetodoPagoConfigService metodoPagoConfigService;

    @GetMapping
    public ResponseEntity<?> listar(
            @RequestParam(name = "estado", required = false) String estado) {
        try {
            return ResponseEntity.ok(metodoPagoConfigService.listar(estado));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al listar metodos de pago" : e.getMessage();
            return ResponseEntity.badRequest().body(Map.of("message", message));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtener(@PathVariable Integer id) {
        try {
            return ResponseEntity.ok(metodoPagoConfigService.obtener(id));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al obtener metodo de pago" : e.getMessage();
            HttpStatus status = message.toLowerCase().contains("no encontrado")
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @PostMapping("/insertar")
    public ResponseEntity<?> insertar(@Valid @RequestBody MetodoPagoConfigCreateRequest request) {
        return crear(request);
    }

    @PostMapping
    public ResponseEntity<?> crear(@Valid @RequestBody MetodoPagoConfigCreateRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(metodoPagoConfigService.crear(request));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al crear metodo de pago" : e.getMessage();
            return ResponseEntity.badRequest().body(Map.of("message", message));
        }
    }

    @PutMapping("/actualizar/{id}")
    public ResponseEntity<?> actualizarDesdeArquitectura(
            @PathVariable Integer id,
            @Valid @RequestBody MetodoPagoConfigUpdateRequest request) {
        return actualizar(id, request);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody MetodoPagoConfigUpdateRequest request) {
        try {
            return ResponseEntity.ok(metodoPagoConfigService.actualizar(id, request));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al actualizar metodo de pago" : e.getMessage();
            HttpStatus status = message.toLowerCase().contains("no encontrado")
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<?> actualizarEstado(
            @PathVariable Integer id,
            @Valid @RequestBody MetodoPagoEstadoUpdateRequest request) {
        try {
            return ResponseEntity.ok(metodoPagoConfigService.actualizarEstado(id, request.estado()));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al actualizar estado del metodo de pago" : e.getMessage();
            HttpStatus status = message.toLowerCase().contains("no encontrado")
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @DeleteMapping("/eliminar/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Integer id) {
        try {
            metodoPagoConfigService.eliminar(id);
            return ResponseEntity.ok(Map.of("message", "Metodo de pago eliminado correctamente"));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al eliminar metodo de pago" : e.getMessage();
            HttpStatus status = message.toLowerCase().contains("no encontrado")
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
