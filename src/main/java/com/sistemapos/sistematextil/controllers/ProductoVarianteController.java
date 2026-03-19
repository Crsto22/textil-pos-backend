package com.sistemapos.sistematextil.controllers;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.services.ProductoVarianteService;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteListadoResumenResponse;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteOfertaListItemResponse;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteOfertaLoteUpdateRequest;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteOfertaUpdateRequest;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteUpdateRequest;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(value = "api/variante", produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class ProductoVarianteController {

    private final ProductoVarianteService service;

    @GetMapping("listar")
    public ResponseEntity<?> listar() {
        try {
            return ResponseEntity.ok(service.listarTodas());
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al listar variantes" : e.getMessage();
            return ResponseEntity.status(resolverStatus(message, HttpStatus.BAD_REQUEST))
                    .body(Map.of("message", message));
        }
    }

    @GetMapping("producto/{id}")
    public ResponseEntity<?> listarPorProducto(@PathVariable Integer id) {
        try {
            return ResponseEntity.ok(service.listarPorProducto(id));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al listar variantes del producto" : e.getMessage();
            return ResponseEntity.status(resolverStatus(message, HttpStatus.BAD_REQUEST))
                    .body(Map.of("message", message));
        }
    }

    @GetMapping("listar-resumen")
    public ResponseEntity<?> listarResumen(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "idCategoria", required = false) Integer idCategoria,
            @RequestParam(name = "idColor", required = false) Integer idColor,
            @RequestParam(name = "conOferta", required = false) Boolean conOferta) {
        try {
            PagedResponse<ProductoVarianteListadoResumenResponse> response = service.listarResumenPaginado(
                    q,
                    page,
                    idCategoria,
                    idColor,
                    conOferta,
                    obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al listar resumen de variantes" : e.getMessage();
            return ResponseEntity.status(resolverStatus(message, HttpStatus.BAD_REQUEST))
                    .body(Map.of("message", message));
        }
    }

    @GetMapping(
            value = "reporte/excel",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<?> reporteExcelDisponibles(Authentication authentication) {
        try {
            byte[] archivo = service.exportarDisponiblesExcel(obtenerCorreoAutenticado(authentication));
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String nombreArchivo = "productos_disponibles_" + ts + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreArchivo + "\"")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(archivo);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al exportar productos disponibles" : e.getMessage();
            return ResponseEntity.status(resolverStatus(message, HttpStatus.BAD_REQUEST))
                    .body(Map.of("message", message));
        }
    }

    @GetMapping("ofertas")
    public ResponseEntity<?> listarConOferta(
            @RequestParam(defaultValue = "0") int page) {
        try {
            PagedResponse<ProductoVarianteOfertaListItemResponse> response = service.listarConOfertaPaginado(page);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al listar variantes con oferta" : e.getMessage();
            return ResponseEntity.status(resolverStatus(message, HttpStatus.BAD_REQUEST))
                    .body(Map.of("message", message));
        }
    }

    @PostMapping("insertar")
    public ResponseEntity<?> crear(@Valid @RequestBody ProductoVariante variante) {
        try {
            return new ResponseEntity<>(service.insertar(variante), HttpStatus.CREATED);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al crear variante" : e.getMessage();
            return ResponseEntity.status(resolverStatus(message, HttpStatus.BAD_REQUEST))
                    .body(Map.of("message", message));
        }
    }

    @PutMapping("actualizar/{id}")
    public ResponseEntity<?> actualizar(
            Authentication authentication,
            @PathVariable Integer id,
            @Valid @RequestBody ProductoVarianteUpdateRequest request) {
        try {
            return ResponseEntity.ok(service.actualizar(id, request, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al actualizar variante" : e.getMessage();
            return ResponseEntity.status(resolverStatus(message, HttpStatus.BAD_REQUEST))
                    .body(Map.of("message", message));
        }
    }

    @PatchMapping("stock/{id}")
    public ResponseEntity<?> actualizarStock(@PathVariable Integer id, @RequestBody Integer stock) {
        try {
            return ResponseEntity.ok(service.actualizarStock(id, stock));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al actualizar stock de variante" : e.getMessage();
            return ResponseEntity.status(resolverStatus(message, HttpStatus.BAD_REQUEST))
                    .body(Map.of("message", message));
        }
    }

    @DeleteMapping("eliminar/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Integer id) {
        try {
            service.eliminar(id);
            return ResponseEntity.ok(Map.of("message", "Variante eliminada logicamente"));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al eliminar variante" : e.getMessage();
            HttpStatus status = esNoEncontrada(message)
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @PatchMapping("precio/{id}")
    public ResponseEntity<?> actualizarPrecio(@PathVariable Integer id, @RequestBody Double precio) {
        try {
            return ResponseEntity.ok(service.actualizarPrecio(id, precio));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al actualizar precio de variante" : e.getMessage();
            return ResponseEntity.status(resolverStatus(message, HttpStatus.BAD_REQUEST))
                    .body(Map.of("message", message));
        }
    }

    @PatchMapping("oferta/{id}")
    public ResponseEntity<?> actualizarOferta(
            @PathVariable Integer id,
            @Valid @RequestBody ProductoVarianteOfertaUpdateRequest request) {
        try {
            return ResponseEntity.ok(service.actualizarOferta(id, request));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al actualizar oferta de variante" : e.getMessage();
            return ResponseEntity.status(resolverStatus(message, HttpStatus.BAD_REQUEST))
                    .body(Map.of("message", message));
        }
    }

    @PatchMapping("ofertas/lote")
    public ResponseEntity<?> actualizarOfertasLote(
            @Valid @RequestBody ProductoVarianteOfertaLoteUpdateRequest request) {
        try {
            return ResponseEntity.ok(service.actualizarOfertasLote(request));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al actualizar ofertas de variantes" : e.getMessage();
            return ResponseEntity.status(resolverStatus(message, HttpStatus.BAD_REQUEST))
                    .body(Map.of("message", message));
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
        if (normalizedMessage.contains("no autenticado")
                || normalizedMessage.contains("no tiene permisos")) {
            return HttpStatus.FORBIDDEN;
        }
        return esNoEncontrada(message) ? HttpStatus.NOT_FOUND : defaultStatus;
    }

    private boolean esNoEncontrada(String message) {
        String normalizedMessage = message.toLowerCase();
        return normalizedMessage.contains("no encontrada")
                || normalizedMessage.contains("no encontrado")
                || normalizedMessage.contains("no existe");
    }

    private String obtenerCorreoAutenticado(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return authentication.getName();
    }
}
