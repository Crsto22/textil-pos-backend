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
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.services.NotaCreditoService;
import com.sistemapos.sistematextil.services.VentaAnulacionService;
import com.sistemapos.sistematextil.services.VentaResumenReporteService;
import com.sistemapos.sistematextil.services.VentaService;
import com.sistemapos.sistematextil.util.notacredito.NotaCreditoCreateRequest;
import com.sistemapos.sistematextil.util.notacredito.NotaCreditoResponse;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.venta.VentaAnulacionRequest;
import com.sistemapos.sistematextil.util.venta.VentaAnulacionResponse;
import com.sistemapos.sistematextil.util.venta.VentaCreateRequest;
import com.sistemapos.sistematextil.util.venta.VentaListItemResponse;
import com.sistemapos.sistematextil.util.venta.VentaReporteResponse;
import com.sistemapos.sistematextil.util.venta.VentaResumenReporteResponse;
import com.sistemapos.sistematextil.util.venta.VentaResponse;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(value = "api/venta", produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class VentaController {

    private final VentaService ventaService;
    private final VentaResumenReporteService ventaResumenReporteService;
    private final VentaAnulacionService ventaAnulacionService;
    private final NotaCreditoService notaCreditoService;

    @GetMapping("/listar")
    public ResponseEntity<?> listar(
            Authentication authentication,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "idUsuario", required = false) Integer idUsuario,
            @RequestParam(name = "idCliente", required = false) Integer idCliente,
            @RequestParam(name = "tipoComprobante", required = false) String tipoComprobante,
            @RequestParam(name = "periodo", required = false) String periodo,
            @RequestParam(name = "fecha", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "idSucursal", required = false) Integer idSucursal,
            @RequestParam(defaultValue = "0") int page) {
        try {
            PagedResponse<VentaListItemResponse> response = ventaService
                    .listarPaginado(
                            q,
                            idUsuario,
                            idCliente,
                            tipoComprobante,
                            periodo,
                            fecha,
                            desde,
                            hasta,
                            idSucursal,
                            page,
                            obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al listar ventas" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @GetMapping("/reporte")
    public ResponseEntity<?> reporte(
            Authentication authentication,
            @RequestParam(name = "agrupar", required = false) String agrupar,
            @RequestParam(name = "periodo", required = false) String periodo,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "idSucursal", required = false) Integer idSucursal,
            @RequestParam(name = "idCliente", required = false) Integer idCliente,
            @RequestParam(name = "incluirAnuladas", defaultValue = "false") boolean incluirAnuladas) {
        try {
            VentaReporteResponse response = ventaService.obtenerReporteVentas(
                    agrupar,
                    periodo,
                    desde,
                    hasta,
                    idSucursal,
                    idCliente,
                    incluirAnuladas,
                    obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al generar reporte de ventas" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @GetMapping("/reporte/resumen")
    public ResponseEntity<?> reporteResumen(
            Authentication authentication,
            @RequestParam(name = "filtro", required = false) String filtro,
            @RequestParam(name = "idSucursal", required = false) Integer idSucursal) {
        try {
            VentaResumenReporteResponse response = ventaResumenReporteService.obtenerReporte(
                    filtro,
                    idSucursal,
                    obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al generar resumen de ventas" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @GetMapping(
            value = "/reporte/pdf",
            produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> reportePdf(
            Authentication authentication,
            @RequestParam(name = "agrupar", required = false) String agrupar,
            @RequestParam(name = "periodo", required = false) String periodo,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "idSucursal", required = false) Integer idSucursal,
            @RequestParam(name = "idCliente", required = false) Integer idCliente,
            @RequestParam(name = "incluirAnuladas", defaultValue = "false") boolean incluirAnuladas) {
        try {
            byte[] archivo = ventaService.exportarReportePdfVentas(
                    agrupar,
                    periodo,
                    desde,
                    hasta,
                    idSucursal,
                    idCliente,
                    incluirAnuladas,
                    obtenerCorreoAutenticado(authentication));

            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String nombreArchivo = "reporte_ventas_" + ts + ".pdf";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreArchivo + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(archivo);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al exportar reporte de ventas PDF" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @GetMapping(
            value = "/reporte/excel",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<?> reporteExcel(
            Authentication authentication,
            @RequestParam(name = "agrupar", required = false) String agrupar,
            @RequestParam(name = "periodo", required = false) String periodo,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "idSucursal", required = false) Integer idSucursal,
            @RequestParam(name = "idCliente", required = false) Integer idCliente,
            @RequestParam(name = "incluirAnuladas", defaultValue = "false") boolean incluirAnuladas) {
        try {
            byte[] archivo = ventaService.exportarReporteVentasExcel(
                    agrupar,
                    periodo,
                    desde,
                    hasta,
                    idSucursal,
                    idCliente,
                    incluirAnuladas,
                    obtenerCorreoAutenticado(authentication));

            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String nombreArchivo = "reporte_ventas_" + ts + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreArchivo + "\"")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(archivo);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al exportar reporte de ventas" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @GetMapping("/detalle/{id}")
    public ResponseEntity<?> detalle(
            Authentication authentication,
            @PathVariable Integer id) {
        try {
            VentaResponse response = ventaService.obtenerDetalle(id, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al obtener detalle de venta" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @GetMapping(value = "/{id}/comprobante/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> descargarComprobantePdf(
            Authentication authentication,
            @PathVariable Integer id) {
        try {
            byte[] archivo = ventaService.generarComprobantePdfA4(id, obtenerCorreoAutenticado(authentication));
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String nombreArchivo = "comprobante_venta_" + id + "_" + ts + ".pdf";

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

    @GetMapping(value = "/{id}/comprobante/ticket", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> descargarTicket(
            Authentication authentication,
            @PathVariable Integer id) {
        try {
            byte[] archivo = ventaService.generarTicket80mm(id, obtenerCorreoAutenticado(authentication));
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String nombreArchivo = "ticket_venta_" + id + "_" + ts + ".pdf";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nombreArchivo + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(archivo);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al generar ticket" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @GetMapping(value = "/{id}/sunat/xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<?> descargarSunatXml(
            Authentication authentication,
            @PathVariable Integer id) {
        try {
            VentaService.ArchivoDescargable archivo = ventaService
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
            VentaService.ArchivoDescargable archivo = ventaService
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

    @PostMapping("/insertar")
    public ResponseEntity<?> insertar(
            Authentication authentication,
            @Valid @RequestBody VentaCreateRequest request) {
        try {
            VentaResponse response = ventaService.registrarVenta(request, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al registrar venta" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @PostMapping("/{id}/sunat/reintentar")
    public ResponseEntity<?> reintentarSunat(
            Authentication authentication,
            @PathVariable Integer id) {
        try {
            VentaResponse response = ventaService.reintentarEmisionSunat(id, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al reenviar comprobante a SUNAT" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @PostMapping("/{id}/anular")
    public ResponseEntity<?> anularVenta(
            Authentication authentication,
            @PathVariable Integer id,
            @Valid @RequestBody VentaAnulacionRequest request) {
        try {
            VentaAnulacionResponse response = ventaAnulacionService
                    .anular(id, request, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al anular venta" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @PostMapping("/{id}/nota-credito")
    public ResponseEntity<?> emitirNotaCredito(
            Authentication authentication,
            @PathVariable Integer id,
            @Valid @RequestBody NotaCreditoCreateRequest request) {
        try {
            NotaCreditoResponse response = notaCreditoService
                    .emitirDesdeVenta(id, request, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al emitir nota de credito" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
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
