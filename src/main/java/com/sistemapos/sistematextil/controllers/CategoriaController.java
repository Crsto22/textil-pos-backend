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

import com.sistemapos.sistematextil.services.CategoriaService;
import com.sistemapos.sistematextil.util.CategoriaCreateRequest;
import com.sistemapos.sistematextil.util.CategoriaListItemResponse;
import com.sistemapos.sistematextil.util.CategoriaUpdateRequest;
import com.sistemapos.sistematextil.util.PagedResponse;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(value = "api/categoria", produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class CategoriaController {

    private final CategoriaService categoriaService;

    @GetMapping("/listar")
    public ResponseEntity<?> listar(Authentication authentication, @RequestParam(defaultValue = "0") int page) {
        try {
            PagedResponse<CategoriaListItemResponse> response = categoriaService
                    .listarPaginado(page, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al listar categorias" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @GetMapping("/buscar")
    public ResponseEntity<?> buscar(
            Authentication authentication,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(defaultValue = "0") int page) {
        try {
            PagedResponse<CategoriaListItemResponse> response = categoriaService
                    .buscarPaginado(q, page, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al buscar categorias" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @PostMapping("/insertar")
    public ResponseEntity<?> crear(Authentication authentication, @Valid @RequestBody CategoriaCreateRequest request) {
        try {
            CategoriaListItemResponse creada = categoriaService.insertar(request, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.status(HttpStatus.CREATED).body(creada);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al crear categoria" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @PutMapping("/actualizar/{id}")
    public ResponseEntity<?> actualizar(
            Authentication authentication,
            @PathVariable Integer id,
            @Valid @RequestBody CategoriaUpdateRequest request) {
        try {
            CategoriaListItemResponse actualizada = categoriaService
                    .actualizar(id, request, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(actualizada);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al actualizar categoria" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @DeleteMapping("/eliminar/{id}")
    public ResponseEntity<?> eliminar(Authentication authentication, @PathVariable Integer id) {
        try {
            categoriaService.eliminar(id, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(Map.of("message", "Categoria eliminada correctamente"));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al eliminar categoria" : e.getMessage();
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
        if (normalizedMessage.contains("no encontrada")
                || normalizedMessage.contains("no encontrado")) {
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
