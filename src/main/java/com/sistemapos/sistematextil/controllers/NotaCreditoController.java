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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.services.NotaCreditoService;
import com.sistemapos.sistematextil.services.VentaService;
import com.sistemapos.sistematextil.util.notacredito.NotaCreditoListItemResponse;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping(value = "api/nota-credito", produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class NotaCreditoController {

    private final NotaCreditoService notaCreditoService;

    @GetMapping("/listar")
    public ResponseEntity<?> listar(
            Authentication authentication,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "idVenta", required = false) Integer idVenta,
            @RequestParam(name = "idUsuario", required = false) Integer idUsuario,
            @RequestParam(name = "idCliente", required = false) Integer idCliente,
            @RequestParam(name = "codigoMotivo", required = false) String codigoMotivo,
            @RequestParam(name = "periodo", required = false) String periodo,
            @RequestParam(name = "fecha", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "idSucursal", required = false) Integer idSucursal,
            @RequestParam(defaultValue = "0") int page) {
        try {
            PagedResponse<NotaCreditoListItemResponse> response = notaCreditoService.listarPaginado(
                    q,
                    idVenta,
                    idUsuario,
                    idCliente,
                    codigoMotivo,
                    periodo,
                    fecha,
                    desde,
                    hasta,
                    idSucursal,
                    page,
                    obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al listar notas de credito" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @GetMapping(value = "/{id}/comprobante/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> descargarComprobantePdf(
            Authentication authentication,
            @PathVariable Integer id) {
        try {
            byte[] archivo = notaCreditoService.generarComprobantePdfA4(id, obtenerCorreoAutenticado(authentication));
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String nombreArchivo = "nota_credito_" + id + "_" + ts + ".pdf";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreArchivo + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(archivo);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al generar comprobante PDF" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @GetMapping(value = "/{id}/sunat/xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<?> descargarSunatXml(
            Authentication authentication,
            @PathVariable Integer id) {
        try {
            VentaService.ArchivoDescargable archivo = notaCreditoService
                    .descargarSunatXml(id, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archivo.nombreArchivo() + "\"")
                    .contentType(MediaType.APPLICATION_XML)
                    .body(archivo.bytes());
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al descargar XML SUNAT" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @GetMapping(value = "/{id}/sunat/cdr", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<?> descargarSunatCdr(
            Authentication authentication,
            @PathVariable Integer id) {
        try {
            VentaService.ArchivoDescargable archivo = notaCreditoService
                    .descargarSunatCdr(id, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archivo.nombreArchivo() + "\"")
                    .contentType(MediaType.parseMediaType(archivo.contentType()))
                    .body(archivo.bytes());
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al descargar CDR SUNAT" : e.getMessage();
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
