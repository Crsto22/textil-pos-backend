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

import com.sistemapos.sistematextil.model.Talla;
import com.sistemapos.sistematextil.services.TallaService;
import com.sistemapos.sistematextil.util.PagedResponse;
import com.sistemapos.sistematextil.util.TallaCreateRequest;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(value = "api/talla", produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class TallaController {

    private final TallaService tallaService;

    @GetMapping("/listar")
    public ResponseEntity<?> listar(@RequestParam(defaultValue = "0") int page) {
        try {
            PagedResponse<Talla> response = tallaService.listarPaginado(page);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al listar tallas" : e.getMessage();
            return ResponseEntity.badRequest().body(Map.of("message", message));
        }
    }

    @GetMapping("/buscar")
    public ResponseEntity<?> buscar(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(defaultValue = "0") int page) {
        try {
            PagedResponse<Talla> response = tallaService.buscarPaginado(q, page);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al buscar tallas" : e.getMessage();
            return ResponseEntity.badRequest().body(Map.of("message", message));
        }
    }

    @PostMapping("/insertar")
    public ResponseEntity<?> crear(@Valid @RequestBody TallaCreateRequest request) {
        try {
            Talla creada = tallaService.insertar(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(creada);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al crear talla" : e.getMessage();
            return ResponseEntity.badRequest().body(Map.of("message", message));
        }
    }

    @PutMapping("/actualizar/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Integer id, @Valid @RequestBody TallaCreateRequest request) {
        try {
            Talla actualizada = tallaService.actualizar(id, request);
            return ResponseEntity.ok(actualizada);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al actualizar talla" : e.getMessage();
            HttpStatus status = message.toLowerCase().contains("no encontrada")
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @DeleteMapping("/eliminar/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Integer id) {
        try {
            tallaService.eliminar(id);
            return ResponseEntity.ok(Map.of("message", "Talla eliminada correctamente"));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al eliminar talla" : e.getMessage();
            HttpStatus status = message.toLowerCase().contains("no encontrada")
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.BAD_REQUEST;
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
}
