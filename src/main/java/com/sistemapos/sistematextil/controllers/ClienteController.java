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

import com.sistemapos.sistematextil.services.ClienteService;
import com.sistemapos.sistematextil.util.cliente.ClienteCreateRequest;
import com.sistemapos.sistematextil.util.cliente.ClienteListItemResponse;
import com.sistemapos.sistematextil.util.cliente.ClienteUpdateRequest;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(value = "api/cliente", produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;

    @GetMapping("/listar")
    public ResponseEntity<?> listar(Authentication authentication, @RequestParam(defaultValue = "0") int page) {
        try {
            return ResponseEntity.ok(clienteService.listarPaginado(page, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al listar clientes" : e.getMessage();
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
            return ResponseEntity.ok(clienteService.buscarPaginado(q, page, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al buscar clientes" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @PostMapping("/insertar")
    public ResponseEntity<?> crear(Authentication authentication, @Valid @RequestBody ClienteCreateRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(clienteService.insertar(request, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al crear cliente" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @PutMapping("/actualizar/{id}")
    public ResponseEntity<?> actualizar(Authentication authentication, @PathVariable Integer id, @Valid @RequestBody ClienteUpdateRequest request) {
        try {
            return ResponseEntity.ok(clienteService.actualizar(id, request, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al actualizar cliente" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @DeleteMapping("/eliminar/{id}")
    public ResponseEntity<?> eliminar(Authentication authentication, @PathVariable Integer id) {
        try {
            clienteService.eliminarLogico(id, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(Map.of("message", "Cliente eliminado logicamente"));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al eliminar cliente" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.NOT_FOUND);
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
        if (normalizedMessage.contains("no encontrado")) {
            return HttpStatus.NOT_FOUND;
        }
        if (normalizedMessage.contains("no tiene permisos") || normalizedMessage.contains("no autenticado")) {
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
