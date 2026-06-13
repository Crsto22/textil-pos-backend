package com.sistemapos.sistematextil.controllers;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.services.EcommerceProductoPublicService;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceInicioResponse;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceProductoDetalleSlugResponse;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceProductoListadoResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(value = "api/public/ecommerce", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class EcommercePublicController {

    private static final String TIENDA_NO_CONFIGURADA = "TIENDA_NO_CONFIGURADA";

    private final EcommerceProductoPublicService ecommerceProductoPublicService;

    @GetMapping("productos")
    public ResponseEntity<EcommerceProductoListadoResponse> listarProductos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "idCategoria", required = false) Integer idCategoria,
            @RequestParam(name = "idColor", required = false) Integer idColor,
            @RequestParam(name = "soloDisponibles", required = false) Boolean soloDisponibles) {
        return ResponseEntity.ok(ecommerceProductoPublicService.listarProductos(
                q,
                page,
                size,
                idCategoria,
                idColor,
                soloDisponibles));
    }

    @GetMapping("inicio")
    public ResponseEntity<EcommerceInicioResponse> inicio() {
        return ResponseEntity.ok(ecommerceProductoPublicService.obtenerInicio());
    }

    @GetMapping("productos/{slug}")
    public ResponseEntity<?> obtenerDetallePorSlug(@PathVariable String slug) {
        try {
            EcommerceProductoDetalleSlugResponse response = ecommerceProductoPublicService.obtenerDetallePorSlug(slug);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Producto no encontrado" : e.getMessage();
            HttpStatus status = TIENDA_NO_CONFIGURADA.equals(message)
                    ? HttpStatus.CONFLICT
                    : HttpStatus.NOT_FOUND;
            String publicMessage = TIENDA_NO_CONFIGURADA.equals(message)
                    ? "Tienda ecommerce no configurada"
                    : message;
            return ResponseEntity.status(status).body(Map.of("message", publicMessage, "code", message));
        }
    }
}
