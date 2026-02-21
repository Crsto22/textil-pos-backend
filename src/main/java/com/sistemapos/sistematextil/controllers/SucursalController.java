package com.sistemapos.sistematextil.controllers;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import com.sistemapos.sistematextil.services.SucursalService;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.sucursal.SucursalCreateRequest;
import com.sistemapos.sistematextil.util.sucursal.SucursalListItemResponse;
import com.sistemapos.sistematextil.util.sucursal.SucursalUpdateRequest;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(value = "api/sucursal", produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class SucursalController {

    private final SucursalService sucursalService;

    @GetMapping("/listar")
    public ResponseEntity<PagedResponse<SucursalListItemResponse>> listar(@RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(sucursalService.listarPaginado(page));
    }

    @GetMapping("/buscar")
    public ResponseEntity<?> buscar(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(defaultValue = "0") int page) {
        try {
            return ResponseEntity.ok(sucursalService.buscarPaginado(q, page));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/insertar")
    public ResponseEntity<?> crear(@Valid @RequestBody SucursalCreateRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(sucursalService.insertar(request));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/actualizar/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Integer id, @Valid @RequestBody SucursalUpdateRequest request) {
        try {
            return ResponseEntity.ok(sucursalService.actualizar(id, request));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al actualizar sucursal" : e.getMessage();
            HttpStatus status = message.toLowerCase().contains("no encontrada")
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @DeleteMapping("/eliminar/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Integer id) {
        try {
            sucursalService.eliminarLogico(id);
            return ResponseEntity.ok(Map.of("message", "Sucursal eliminada logicamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
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
}
