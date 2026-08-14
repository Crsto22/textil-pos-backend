package com.sistemapos.sistematextil.controllers;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.services.ConsultaCpePublicService;
import com.sistemapos.sistematextil.services.VentaService;
import com.sistemapos.sistematextil.util.cpe.ConsultaCpeRequest;
import com.sistemapos.sistematextil.util.cpe.ConsultaCpeResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(value = "api/public/cpe", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ConsultaCpePublicController {

    private final ConsultaCpePublicService consultaCpePublicService;

    @PostMapping("consulta")
    public ResponseEntity<?> consultar(@Valid @RequestBody ConsultaCpeRequest request) {
        try {
            ConsultaCpeResponse response = consultaCpePublicService.consultar(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return error(e, "Comprobante no encontrado", HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping(value = "descargar/{token}/{archivo}", produces = {
            MediaType.APPLICATION_PDF_VALUE,
            MediaType.APPLICATION_XML_VALUE,
            "application/zip"
    })
    public ResponseEntity<?> descargar(
            @PathVariable String token,
            @PathVariable String archivo) {
        try {
            VentaService.ArchivoDescargable descargable = consultaCpePublicService.descargar(token, archivo);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + descargable.nombreArchivo() + "\"")
                    .contentType(MediaType.parseMediaType(descargable.contentType()))
                    .body(descargable.bytes());
        } catch (RuntimeException e) {
            return error(e, "Archivo no disponible", HttpStatus.BAD_REQUEST);
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

    private ResponseEntity<?> error(RuntimeException e, String fallback, HttpStatus defaultStatus) {
        String message = e.getMessage() == null ? fallback : e.getMessage();
        HttpStatus status = resolverStatus(message, defaultStatus);
        return ResponseEntity.status(status).body(Map.of("message", message));
    }

    private HttpStatus resolverStatus(String message, HttpStatus defaultStatus) {
        String lower = message.toLowerCase();
        if (lower.contains("no encontrad")) return HttpStatus.NOT_FOUND;
        if (lower.contains("no disponible") || lower.contains("no tiene")) return HttpStatus.CONFLICT;
        if (lower.contains("token")) return HttpStatus.UNAUTHORIZED;
        return defaultStatus;
    }
}
