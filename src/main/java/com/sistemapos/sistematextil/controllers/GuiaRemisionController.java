package com.sistemapos.sistematextil.controllers;

import java.util.Map;

import org.springframework.http.HttpHeaders;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.services.GuiaRemisionService;
import com.sistemapos.sistematextil.util.guiaremision.GuiaRemisionCreateRequest;
import com.sistemapos.sistematextil.util.guiaremision.GuiaRemisionResponse;
import com.sistemapos.sistematextil.util.guiaremision.GuiaRemisionUpdateRequest;
import com.sistemapos.sistematextil.util.guiaremision.GuiaRemisionVentaAutocompleteResponse;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * API de Guias de Remision - exclusivamente motivo 04:
 * Traslado entre establecimientos de la misma empresa.
 */
@RestController
@RequestMapping(value = "api/guia-remision", produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class GuiaRemisionController {

    private final GuiaRemisionService guiaRemisionService;

    @GetMapping
    public ResponseEntity<?> listar(
            Authentication authentication,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "idSucursal", required = false) Integer idSucursal,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "sunatEstado", required = false) String sunatEstado,
            @RequestParam(defaultValue = "0") int page) {
        try {
            PagedResponse<GuiaRemisionResponse> response = guiaRemisionService
                    .listarPaginado(q, idSucursal, estado, sunatEstado, page,
                            obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return error(e, "Error al listar guias de remision", HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detalle(
            Authentication authentication,
            @PathVariable Integer id) {
        try {
            GuiaRemisionResponse response = guiaRemisionService
                    .obtenerDetalle(id, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return error(e, "Error al obtener detalle de guia de remision", HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/autocompletar/venta")
    public ResponseEntity<?> autocompletarDesdeVenta(
            Authentication authentication,
            @RequestParam(name = "tipoDocumento", required = false) String tipoDocumento,
            @RequestParam(name = "serie") String serie,
            @RequestParam(name = "numero") String numero) {
        try {
            GuiaRemisionVentaAutocompleteResponse response = guiaRemisionService
                    .autocompletarDesdeVenta(
                            tipoDocumento,
                            serie,
                            numero,
                            obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return error(e, "Error al buscar venta para guia de remision", HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping
    public ResponseEntity<?> crear(
            Authentication authentication,
            @Valid @RequestBody GuiaRemisionCreateRequest request) {
        try {
            GuiaRemisionResponse response = guiaRemisionService
                    .crear(request, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            return error(e, "Error al crear guia de remision", HttpStatus.BAD_REQUEST);
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> editarBorrador(
            Authentication authentication,
            @PathVariable Integer id,
            @Valid @RequestBody GuiaRemisionUpdateRequest request) {
        try {
            GuiaRemisionResponse response = guiaRemisionService
                    .editarBorrador(id, request, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return error(e, "Error al editar borrador de guia de remision", HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/{id}/emitir")
    public ResponseEntity<?> emitir(
            Authentication authentication,
            @PathVariable Integer id) {
        try {
            GuiaRemisionResponse response = guiaRemisionService
                    .emitir(id, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return error(e, "Error al emitir guia de remision", HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/{id}/consultar-cdr")
    public ResponseEntity<?> consultarCdr(
            Authentication authentication,
            @PathVariable Integer id) {
        try {
            GuiaRemisionResponse response = guiaRemisionService
                    .consultarCdr(id, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return error(e, "Error al consultar CDR SUNAT", HttpStatus.BAD_REQUEST);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> anular(
            Authentication authentication,
            @PathVariable Integer id) {
        try {
            GuiaRemisionResponse response = guiaRemisionService
                    .anular(id, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return error(e, "Error al anular guia de remision", HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> descargarPdf(
            Authentication authentication,
            @PathVariable Integer id) {
        try {
            GuiaRemisionService.ArchivoDescargable archivo = guiaRemisionService
                    .descargarPdf(id, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archivo.nombreArchivo() + "\"")
                    .contentType(MediaType.parseMediaType(archivo.contentType()))
                    .body(archivo.bytes());
        } catch (RuntimeException e) {
            return error(e, "Error al generar PDF de guia de remision", HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping(value = "/{id}/sunat/xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<?> descargarSunatXml(
            Authentication authentication,
            @PathVariable Integer id) {
        try {
            GuiaRemisionService.ArchivoDescargable archivo = guiaRemisionService
                    .descargarSunatXml(id, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + archivo.nombreArchivo() + "\"")
                    .contentType(MediaType.APPLICATION_XML)
                    .body(archivo.bytes());
        } catch (RuntimeException e) {
            return error(e, "Error al descargar XML SUNAT", HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping(value = "/{id}/sunat/cdr", produces = { MediaType.APPLICATION_XML_VALUE, "application/zip" })
    public ResponseEntity<?> descargarSunatCdr(
            Authentication authentication,
            @PathVariable Integer id) {
        try {
            GuiaRemisionService.ArchivoDescargable archivo = guiaRemisionService
                    .descargarSunatCdr(id, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + archivo.nombreArchivo() + "\"")
                    .contentType(MediaType.parseMediaType(archivo.contentType()))
                    .body(archivo.bytes());
        } catch (RuntimeException e) {
            return error(e, "Error al descargar CDR SUNAT", HttpStatus.BAD_REQUEST);
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

    private ResponseEntity<?> error(RuntimeException e, String fallback, HttpStatus defaultStatus) {
        String message = e.getMessage() == null ? fallback : e.getMessage();
        HttpStatus status = resolverStatus(message, defaultStatus);
        return ResponseEntity.status(status).body(Map.of("message", message));
    }

    private HttpStatus resolverStatus(String message, HttpStatus defaultStatus) {
        String lower = message.toLowerCase();
        if (lower.contains("no encontrad")) return HttpStatus.NOT_FOUND;
        if (lower.contains("no autenticado") || lower.contains("no tiene permisos")) return HttpStatus.FORBIDDEN;
        if (lower.contains("no se puede") || lower.contains("ya fue aceptada")
                || lower.contains("solo se puede") || lower.contains("solo se pueden")) return HttpStatus.CONFLICT;
        return defaultStatus;
    }

    private String obtenerCorreoAutenticado(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return authentication.getName();
    }
}
