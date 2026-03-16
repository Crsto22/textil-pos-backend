package com.sistemapos.sistematextil.controllers;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sistemapos.sistematextil.services.SunatConfigService;
import com.sistemapos.sistematextil.util.sunatconfig.SunatConfigUpsertRequest;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(value = "api/config/sunat", produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class SunatConfigController {

    private final SunatConfigService sunatConfigService;

    @GetMapping
    public ResponseEntity<?> obtener() {
        try {
            return ResponseEntity.ok(sunatConfigService.obtener());
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al obtener configuracion SUNAT" : e.getMessage();
            return ResponseEntity.status(resolverStatus(message)).body(Map.of("message", message));
        }
    }

    @PutMapping
    public ResponseEntity<?> guardar(@Valid @RequestBody SunatConfigUpsertRequest request) {
        try {
            return ResponseEntity.ok(sunatConfigService.guardar(request));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al guardar configuracion SUNAT" : e.getMessage();
            return ResponseEntity.status(resolverStatus(message)).body(Map.of("message", message));
        }
    }

    @PostMapping(value = "/certificado", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> subirCertificado(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "certificadoPassword", required = false) String certificadoPassword) {
        try {
            return ResponseEntity.ok(sunatConfigService.subirCertificado(file, certificadoPassword));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al subir certificado digital" : e.getMessage();
            return ResponseEntity.status(resolverStatus(message)).body(Map.of("message", message));
        }
    }

    @PostMapping("/probar-conexion")
    public ResponseEntity<?> probarConexion() {
        try {
            return ResponseEntity.ok(sunatConfigService.probarConexion());
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al probar configuracion SUNAT" : e.getMessage();
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
        if (normalized.contains("no hay")
                || normalized.contains("no existe")
                || normalized.contains("no encontrada")
                || normalized.contains("no encontrado")) {
            return HttpStatus.NOT_FOUND;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
