package com.sistemapos.sistematextil.controllers;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.services.ComprobanteConfigService;
import com.sistemapos.sistematextil.util.comprobante.ComprobanteConfigCreateRequest;
import com.sistemapos.sistematextil.util.comprobante.ComprobanteConfigUpdateRequest;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(value = "api/config/comprobantes", produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class ComprobanteConfigController {

    private final ComprobanteConfigService comprobanteConfigService;

    @GetMapping
    public ResponseEntity<?> listar(
            @RequestParam(name = "activo", required = false) String activo,
            @RequestParam(name = "habilitadoVenta", required = false) Boolean habilitadoVenta) {
        try {
            return ResponseEntity.ok(comprobanteConfigService.listar(activo, habilitadoVenta));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al listar comprobantes" : e.getMessage();
            return ResponseEntity.badRequest().body(Map.of("message", message));
        }
    }

    @GetMapping("/listar")
    public ResponseEntity<?> listarPaginado(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "activo", required = false) String activo,
            @RequestParam(name = "habilitadoVenta", required = false) Boolean habilitadoVenta) {
        try {
            return ResponseEntity.ok(comprobanteConfigService.listarPaginado(page, activo, habilitadoVenta));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al listar comprobantes" : e.getMessage();
            return ResponseEntity.badRequest().body(Map.of("message", message));
        }
    }

    @GetMapping("/buscar")
    public ResponseEntity<?> buscar(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "activo", required = false) String activo,
            @RequestParam(name = "habilitadoVenta", required = false) Boolean habilitadoVenta) {
        try {
            return ResponseEntity.ok(
                    comprobanteConfigService.buscarPaginado(q, page, activo, habilitadoVenta));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al buscar comprobantes" : e.getMessage();
            return ResponseEntity.badRequest().body(Map.of("message", message));
        }
    }

    @GetMapping("/detalle/{id}")
    public ResponseEntity<?> obtenerDetalle(@PathVariable Integer id) {
        return obtener(id);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtener(@PathVariable Integer id) {
        try {
            return ResponseEntity.ok(comprobanteConfigService.obtener(id));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al obtener comprobante" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @PostMapping("/insertar")
    public ResponseEntity<?> insertar(@Valid @RequestBody ComprobanteConfigCreateRequest request) {
        return crear(request);
    }

    @PostMapping
    public ResponseEntity<?> crear(@Valid @RequestBody ComprobanteConfigCreateRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(comprobanteConfigService.insertar(request));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al crear comprobante" : e.getMessage();
            return ResponseEntity.badRequest().body(Map.of("message", message));
        }
    }

    @PutMapping("/actualizar/{id}")
    public ResponseEntity<?> actualizarDesdeArquitectura(
            @PathVariable Integer id,
            @Valid @RequestBody ComprobanteConfigUpdateRequest request) {
        return actualizar(id, request);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody ComprobanteConfigUpdateRequest request) {
        try {
            return ResponseEntity.ok(comprobanteConfigService.actualizar(id, request));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al actualizar comprobante" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
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

    private HttpStatus resolverStatus(String message, HttpStatus defaultStatus) {
        String messageLower = message.toLowerCase();
        if (messageLower.contains("no encontrado")
                || messageLower.contains("no encontrada")) {
            return HttpStatus.NOT_FOUND;
        }
        return defaultStatus;
    }
}
