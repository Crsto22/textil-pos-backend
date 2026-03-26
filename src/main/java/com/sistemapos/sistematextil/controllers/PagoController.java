package com.sistemapos.sistematextil.controllers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.services.PagoService;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.pago.PagoActualizarCodigoRequest;
import com.sistemapos.sistematextil.util.pago.PagoListItemResponse;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(value = "api/pago", produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class PagoController {

    private final PagoService pagoService;

    @GetMapping("/listar")
    public ResponseEntity<?> listar(
            Authentication authentication,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "idVenta", required = false) Integer idVenta,
            @RequestParam(name = "idUsuario", required = false) Integer idUsuario,
            @RequestParam(name = "idMetodoPago", required = false) Integer idMetodoPago,
            @RequestParam(name = "idSucursal", required = false) Integer idSucursal,
            @RequestParam(name = "estadoVenta", required = false) String estadoVenta,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int page) {
        try {
            PagedResponse<PagoListItemResponse> response = pagoService.listarPaginado(
                    q,
                    idVenta,
                    idUsuario,
                    idMetodoPago,
                    idSucursal,
                    estadoVenta,
                    desde,
                    hasta,
                    page,
                    obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al listar pagos" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @GetMapping(value = "/reporte/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> reportePdf(
            Authentication authentication,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "idVenta", required = false) Integer idVenta,
            @RequestParam(name = "idUsuario", required = false) Integer idUsuario,
            @RequestParam(name = "idMetodoPago", required = false) Integer idMetodoPago,
            @RequestParam(name = "idSucursal", required = false) Integer idSucursal,
            @RequestParam(name = "estadoVenta", required = false) String estadoVenta,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        try {
            byte[] archivo = pagoService.generarReportePagosPdf(
                    q,
                    idVenta,
                    idUsuario,
                    idMetodoPago,
                    idSucursal,
                    estadoVenta,
                    desde,
                    hasta,
                    obtenerCorreoAutenticado(authentication));
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String nombreArchivo = "reporte_pagos_" + ts + ".pdf";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreArchivo + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(archivo);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al generar reporte PDF de pagos" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @PutMapping("/{idPago}/codigo-operacion")
    public ResponseEntity<?> actualizarCodigoOperacion(
            Authentication authentication,
            @PathVariable Integer idPago,
            @Valid @RequestBody PagoActualizarCodigoRequest request) {
        try {
            PagoListItemResponse response = pagoService.actualizarCodigoOperacion(
                    idPago,
                    request,
                    obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al actualizar codigo de operacion" : e.getMessage();
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
