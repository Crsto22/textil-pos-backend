package com.sistemapos.sistematextil.controllers;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.services.CotizacionService;
import com.sistemapos.sistematextil.util.cotizacion.CotizacionConvertirVentaRequest;
import com.sistemapos.sistematextil.util.cotizacion.CotizacionCreateRequest;
import com.sistemapos.sistematextil.util.cotizacion.CotizacionEstadoUpdateRequest;
import com.sistemapos.sistematextil.util.cotizacion.CotizacionUpdateRequest;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(value = "api/cotizacion", produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class CotizacionController {

    private final CotizacionService cotizacionService;

    @GetMapping("/listar")
    public ResponseEntity<?> listar(
            Authentication authentication,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "idUsuario", required = false) Integer idUsuario,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "periodo", required = false) String periodo,
            @RequestParam(name = "fecha", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "idSucursal", required = false) Integer idSucursal,
            @RequestParam(defaultValue = "0") int page) {
        try {
            return ResponseEntity.ok(cotizacionService.listarPaginado(
                    q,
                    idUsuario,
                    estado,
                    periodo,
                    fecha,
                    desde,
                    hasta,
                    idSucursal,
                    page,
                    obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al listar cotizaciones" : e.getMessage();
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
            @RequestParam(name = "estado", required = false) String estado) {
        try {
            return ResponseEntity.ok(cotizacionService.obtenerReporteCotizaciones(
                    agrupar,
                    periodo,
                    desde,
                    hasta,
                    idSucursal,
                    estado,
                    obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al generar reporte de cotizaciones" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @GetMapping("/detalle/{id}")
    public ResponseEntity<?> detalle(
            Authentication authentication,
            @PathVariable Integer id) {
        try {
            return ResponseEntity.ok(cotizacionService.obtenerDetalle(id, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al obtener detalle de cotizacion" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @PostMapping("/insertar")
    public ResponseEntity<?> insertar(
            Authentication authentication,
            @Valid @RequestBody CotizacionCreateRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(cotizacionService.insertar(request, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al registrar cotizacion" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @PutMapping("/actualizar/{id}")
    public ResponseEntity<?> actualizar(
            Authentication authentication,
            @PathVariable Integer id,
            @Valid @RequestBody CotizacionUpdateRequest request) {
        try {
            return ResponseEntity.ok(cotizacionService.actualizar(id, request, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al actualizar cotizacion" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @PatchMapping("/estado/{id}")
    public ResponseEntity<?> actualizarEstado(
            Authentication authentication,
            @PathVariable Integer id,
            @Valid @RequestBody CotizacionEstadoUpdateRequest request) {
        try {
            return ResponseEntity.ok(cotizacionService.actualizarEstado(id, request, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al actualizar estado de cotizacion" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @PostMapping("/{id}/convertir-a-venta")
    public ResponseEntity<?> convertirAVenta(
            Authentication authentication,
            @PathVariable Integer id,
            @Valid @RequestBody CotizacionConvertirVentaRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(cotizacionService.convertirAVenta(id, request, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al convertir cotizacion a venta" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @DeleteMapping("/eliminar/{id}")
    public ResponseEntity<?> eliminar(Authentication authentication, @PathVariable Integer id) {
        try {
            cotizacionService.eliminarLogico(id, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(Map.of("message", "Cotizacion eliminada logicamente"));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al eliminar cotizacion" : e.getMessage();
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
