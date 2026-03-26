package com.sistemapos.sistematextil.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.model.HistorialStock;
import com.sistemapos.sistematextil.services.HistorialStockService;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping("api/historial-stock")
@AllArgsConstructor
public class HistorialStockController {

    private final HistorialStockService service;

    @GetMapping("listar")
    public ResponseEntity<?> listarTodo(Authentication authentication) {
        try {
            return ResponseEntity.ok(service.listarTodo(obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al listar historial de stock" : e.getMessage();
            return ResponseEntity.status(resolverStatus(message)).body(Map.of("message", message));
        }
    }

    @GetMapping("producto/{id}")
    public ResponseEntity<?> porProducto(Authentication authentication, @PathVariable Integer id) {
        try {
            List<HistorialStock> historial = service.listarPorProducto(id, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(historial);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al listar historial por producto" : e.getMessage();
            return ResponseEntity.status(resolverStatus(message)).body(Map.of("message", message));
        }
    }

    @GetMapping("variante/{id}")
    public ResponseEntity<?> porVariante(Authentication authentication, @PathVariable Integer id) {
        try {
            List<HistorialStock> historial = service.listarPorVariante(id, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(historial);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al listar historial por variante" : e.getMessage();
            return ResponseEntity.status(resolverStatus(message)).body(Map.of("message", message));
        }
    }

    private HttpStatus resolverStatus(String message) {
        String normalizedMessage = message.toLowerCase();
        if (normalizedMessage.contains("no autenticado")) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (normalizedMessage.contains("no encontrado")
                || normalizedMessage.contains("no encontrada")) {
            return HttpStatus.NOT_FOUND;
        }
        if (normalizedMessage.contains("no tiene permisos")) {
            return HttpStatus.FORBIDDEN;
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
