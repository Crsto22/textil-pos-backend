package com.sistemapos.sistematextil.controllers;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sistemapos.sistematextil.services.EcommercePortadaService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(value = "api/config/ecommerce/portadas", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class EcommerceConfigController {

    private final EcommercePortadaService portadaService;

    @GetMapping
    public ResponseEntity<?> listar() {
        return ResponseEntity.ok(portadaService.listarAdmin());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> crear(
            @RequestParam("desktop") MultipartFile desktop,
            @RequestParam("mobile") MultipartFile mobile) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(portadaService.crear(desktop, mobile));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", mensaje(e)));
        }
    }

    @PatchMapping("{id}/estado")
    public ResponseEntity<?> cambiarEstado(@PathVariable Integer id, @RequestParam("estado") String estado) {
        try {
            return ResponseEntity.ok(portadaService.cambiarEstado(id, estado));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", mensaje(e)));
        }
    }

    @DeleteMapping("{id}")
    public ResponseEntity<?> eliminar(@PathVariable Integer id) {
        try {
            portadaService.eliminar(id);
            return ResponseEntity.ok(Map.of("message", "Portada eliminada correctamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", mensaje(e)));
        }
    }

    private String mensaje(RuntimeException e) {
        return e.getMessage() == null ? "No se pudo procesar la solicitud" : e.getMessage();
    }
}
