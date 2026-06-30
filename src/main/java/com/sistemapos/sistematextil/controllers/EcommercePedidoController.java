package com.sistemapos.sistematextil.controllers;

import java.util.Map;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.services.EcommercePedidoService;
import com.sistemapos.sistematextil.util.ecommerce.EcommercePedidoAceptarRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(value = "api/ecommerce/pedidos", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class EcommercePedidoController {

    private final EcommercePedidoService ecommercePedidoService;

    @GetMapping
    public ResponseEntity<?> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(ecommercePedidoService.listarAdmin(
                    estado,
                    q,
                    fechaDesde,
                    fechaHasta,
                    page,
                    size,
                    correo(authentication)));
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @GetMapping(
            value = "reporte/excel",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<?> reporteExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            Authentication authentication) {
        try {
            byte[] archivo = ecommercePedidoService.exportarPedidosExcel(fechaDesde, fechaHasta, correo(authentication));
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reporte_pedidos_ecommerce_" + ts + ".xlsx\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(archivo);
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @GetMapping("{id}")
    public ResponseEntity<?> obtener(@PathVariable Integer id, Authentication authentication) {
        try {
            return ResponseEntity.ok(ecommercePedidoService.obtenerAdmin(id, correo(authentication)));
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PostMapping("{id}/aceptar")
    public ResponseEntity<?> aceptar(
            @PathVariable Integer id,
            @Valid @RequestBody EcommercePedidoAceptarRequest request,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(ecommercePedidoService.aceptar(id, request, correo(authentication)));
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PostMapping("{id}/cancelar")
    public ResponseEntity<?> cancelar(@PathVariable Integer id, Authentication authentication) {
        try {
            return ResponseEntity.ok(ecommercePedidoService.cancelar(id, correo(authentication)));
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    private String correo(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }

    private String mensaje(RuntimeException e) {
        return e.getMessage() == null ? "No se pudo procesar la solicitud" : e.getMessage();
    }

    private ResponseEntity<Map<String, String>> error(RuntimeException e) {
        HttpStatus status = e instanceof AccessDeniedException ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Map.of("message", mensaje(e)));
    }
}
