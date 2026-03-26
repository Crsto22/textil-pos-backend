package com.sistemapos.sistematextil.controllers;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.services.ImportacionProductoHistorialService;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.producto.ProductoImportHistorialListItemResponse;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping(value = "api/producto/importacion-historial", produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class ImportacionProductoHistorialController {

    private final ImportacionProductoHistorialService importacionProductoHistorialService;

    @GetMapping("/listar")
    public ResponseEntity<?> listar(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "idSucursal", required = false) Integer idSucursal) {
        try {
            PagedResponse<ProductoImportHistorialListItemResponse> response = importacionProductoHistorialService
                    .listarPaginado(page, idSucursal, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null
                    ? "Error al listar historial de importaciones"
                    : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    private HttpStatus resolverStatus(String message, HttpStatus defaultStatus) {
        String normalizedMessage = message.toLowerCase();
        if (normalizedMessage.contains("no encontrado")
                || normalizedMessage.contains("no encontrada")) {
            return HttpStatus.NOT_FOUND;
        }
        if (normalizedMessage.contains("no autenticado")
                || normalizedMessage.contains("no tiene permisos")) {
            return HttpStatus.FORBIDDEN;
        }
        return defaultStatus;
    }

    private String obtenerCorreoAutenticado(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return authentication.getName();
    }
}
