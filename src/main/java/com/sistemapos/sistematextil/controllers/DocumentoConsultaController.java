package com.sistemapos.sistematextil.controllers;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.services.DocumentoConsultaService;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping(value = "api/documento", produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class DocumentoConsultaController {

    private final DocumentoConsultaService documentoConsultaService;

    @GetMapping("/dni/{dni}")
    public ResponseEntity<?> consultarDni(@PathVariable String dni) {
        try {
            return ResponseEntity.ok(documentoConsultaService.consultarDni(dni));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al consultar DNI" : e.getMessage();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
        }
    }

    @GetMapping("/ruc/{ruc}")
    public ResponseEntity<?> consultarRuc(@PathVariable String ruc) {
        try {
            return ResponseEntity.ok(documentoConsultaService.consultarRuc(ruc));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al consultar RUC" : e.getMessage();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
        }
    }
}
