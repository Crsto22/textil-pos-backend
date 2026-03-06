package com.sistemapos.sistematextil.controllers;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sistemapos.sistematextil.services.ProductoImagenService;
import com.sistemapos.sistematextil.services.ProductoImportService;
import com.sistemapos.sistematextil.services.ProductoService;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.producto.ProductoCompletoCreateRequest;
import com.sistemapos.sistematextil.util.producto.ProductoCompletoResponse;
import com.sistemapos.sistematextil.util.producto.ProductoCompletoUpdateRequest;
import com.sistemapos.sistematextil.util.producto.ProductoCreateRequest;
import com.sistemapos.sistematextil.util.producto.ProductoDetalleResponse;
import com.sistemapos.sistematextil.util.producto.ProductoImagenEditResponse;
import com.sistemapos.sistematextil.util.producto.ProductoImagenUploadResponse;
import com.sistemapos.sistematextil.util.producto.ProductoImportResponse;
import com.sistemapos.sistematextil.util.producto.ProductoListadoResumenResponse;
import com.sistemapos.sistematextil.util.producto.ProductoListItemResponse;
import com.sistemapos.sistematextil.util.producto.ProductoUpdateRequest;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(value = "api/producto", produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class ProductoController {

    private final ProductoService productoService;
    private final ProductoImagenService productoImagenService;
    private final ProductoImportService productoImportService;

    @GetMapping("/listar")
    public ResponseEntity<?> listar(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "idCategoria", required = false) Integer idCategoria,
            @RequestParam(name = "idColor", required = false) Integer idColor) {
        try {
            PagedResponse<ProductoListItemResponse> response = productoService
                    .listarPaginado(page, idCategoria, idColor, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al listar productos" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @GetMapping("/listar-resumen")
    public ResponseEntity<?> listarResumen(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "idCategoria", required = false) Integer idCategoria,
            @RequestParam(name = "idColor", required = false) Integer idColor) {
        try {
            PagedResponse<ProductoListadoResumenResponse> response = productoService
                    .listarResumenPaginado(page, idCategoria, idColor, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al listar productos" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @GetMapping("/buscar")
    public ResponseEntity<?> buscar(
            Authentication authentication,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "idCategoria", required = false) Integer idCategoria,
            @RequestParam(name = "idColor", required = false) Integer idColor,
            @RequestParam(defaultValue = "0") int page) {
        try {
            PagedResponse<ProductoListadoResumenResponse> response = productoService
                    .buscarPaginado(q, page, idCategoria, idColor, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al buscar productos" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @GetMapping("/detalle/{id}")
    public ResponseEntity<?> obtenerDetalle(Authentication authentication, @PathVariable Integer id) {
        try {
            ProductoDetalleResponse detalle = productoService.obtenerDetalle(id, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(detalle);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al obtener detalle del producto" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @PostMapping("/insertar")
    public ResponseEntity<?> crear(Authentication authentication, @Valid @RequestBody ProductoCreateRequest request) {
        try {
            ProductoListItemResponse creado = productoService.insertar(request, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.status(HttpStatus.CREATED).body(creado);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al crear producto" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @PostMapping(value = "/imagenes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> subirImagenes(
            @RequestParam(name = "productoId", required = false) Integer productoId,
            @RequestParam("colorId") Integer colorId,
            @RequestParam("files") java.util.List<MultipartFile> files) {
        try {
            ProductoImagenUploadResponse response = productoImagenService.subirImagenes(productoId, colorId, files);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al subir imagenes" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @PostMapping("/insertar-completo")
    public ResponseEntity<?> crearCompleto(
            Authentication authentication,
            @Valid @RequestBody ProductoCompletoCreateRequest request) {
        try {
            ProductoCompletoResponse creado = productoService.insertarCompleto(
                    request,
                    obtenerCorreoAutenticado(authentication));
            return ResponseEntity.status(HttpStatus.CREATED).body(creado);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al crear producto completo" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importarDesdeExcel(
            Authentication authentication,
            @RequestParam("file") MultipartFile file) {
        try {
            ProductoImportResponse response = productoImportService
                    .importarDesdeExcel(file, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al importar productos" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @PutMapping("/actualizar-completo/{id}")
    public ResponseEntity<?> actualizarCompleto(
            Authentication authentication,
            @PathVariable Integer id,
            @Valid @RequestBody ProductoCompletoUpdateRequest request) {
        try {
            ProductoCompletoResponse actualizado = productoService.actualizarCompleto(
                    id,
                    request,
                    obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(actualizado);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al actualizar producto completo" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @PutMapping(value = "/imagenes/{idColorImagen}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> reemplazarImagen(
            Authentication authentication,
            @PathVariable Integer idColorImagen,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "orden", required = false) Integer orden,
            @RequestParam(name = "esPrincipal", required = false) Boolean esPrincipal) {
        try {
            ProductoImagenEditResponse response = productoImagenService.reemplazarImagen(
                    idColorImagen,
                    obtenerCorreoAutenticado(authentication),
                    file,
                    orden,
                    esPrincipal);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al reemplazar imagen" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @PutMapping("/actualizar/{id}")
    public ResponseEntity<?> actualizar(
            Authentication authentication,
            @PathVariable Integer id,
            @Valid @RequestBody ProductoUpdateRequest request) {
        try {
            ProductoListItemResponse actualizado = productoService
                    .actualizar(id, request, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(actualizado);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al actualizar producto" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @DeleteMapping("/eliminar/{id}")
    public ResponseEntity<?> eliminar(Authentication authentication, @PathVariable Integer id) {
        try {
            productoService.eliminar(id, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(Map.of("message", "Producto eliminado correctamente"));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al eliminar producto" : e.getMessage();
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
