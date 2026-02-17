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

import com.sistemapos.sistematextil.model.Color;
import com.sistemapos.sistematextil.services.ColorService;
import com.sistemapos.sistematextil.util.ColorCreateRequest;
import com.sistemapos.sistematextil.util.PagedResponse;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(value = "api/color", produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class ColorController {

    private final ColorService colorService;

    @GetMapping("/listar")
    public ResponseEntity<?> listar(@RequestParam(defaultValue = "0") int page) {
        try {
            PagedResponse<Color> response = colorService.listarPaginado(page);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al listar colores" : e.getMessage();
            return ResponseEntity.badRequest().body(Map.of("message", message));
        }
    }

    @GetMapping("/buscar")
    public ResponseEntity<?> buscar(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(defaultValue = "0") int page) {
        try {
            PagedResponse<Color> response = colorService.buscarPaginado(q, page);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al buscar colores" : e.getMessage();
            return ResponseEntity.badRequest().body(Map.of("message", message));
        }
    }

    @PostMapping("/insertar")
    public ResponseEntity<?> crear(@Valid @RequestBody ColorCreateRequest request) {
        try {
            Color creado = colorService.insertar(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(creado);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al crear color" : e.getMessage();
            return ResponseEntity.badRequest().body(Map.of("message", message));
        }
    }

    @PutMapping("/actualizar/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Integer id, @Valid @RequestBody ColorCreateRequest request) {
        try {
            Color actualizado = colorService.actualizar(id, request);
            return ResponseEntity.ok(actualizado);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al actualizar color" : e.getMessage();
            HttpStatus status = message.toLowerCase().contains("no encontrado")
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @DeleteMapping("/eliminar/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Integer id) {
        try {
            colorService.eliminar(id);
            return ResponseEntity.ok(Map.of("message", "Color eliminado correctamente"));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al eliminar color" : e.getMessage();
            HttpStatus status = message.toLowerCase().contains("no encontrado")
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
