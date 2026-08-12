package com.sistemapos.sistematextil.controllers;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.services.EcommerceConfigService;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceContactoRequest;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(value = "api/config/ecommerce/contacto", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class EcommerceContactoConfigController {

    private final EcommerceConfigService ecommerceConfigService;

    @GetMapping
    public ResponseEntity<?> obtener() {
        return ResponseEntity.ok(ecommerceConfigService.obtenerContacto());
    }

    @PutMapping
    public ResponseEntity<?> guardar(@RequestBody EcommerceContactoRequest request) {
        try {
            return ResponseEntity.ok(ecommerceConfigService.guardarContacto(
                    request == null ? null : request.whatsappCelular()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", mensaje(e)));
        }
    }

    private String mensaje(RuntimeException e) {
        return e.getMessage() == null ? "No se pudo guardar el contacto ecommerce" : e.getMessage();
    }
}
