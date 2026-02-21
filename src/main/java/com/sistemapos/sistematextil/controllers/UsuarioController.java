package com.sistemapos.sistematextil.controllers;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.services.UsuarioService;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.usuario.Rol;
import com.sistemapos.sistematextil.util.usuario.UsuarioListItemResponse;
import com.sistemapos.sistematextil.util.usuario.UsuarioResetPasswordRequest;
import com.sistemapos.sistematextil.util.usuario.UsuarioUpdateRequest;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(value = "api/usuario", produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping("/listar")
    public ResponseEntity<PagedResponse<UsuarioListItemResponse>> listar(@RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(usuarioService.listarPaginado(page));
    }

    @GetMapping("/buscar")
    public ResponseEntity<?> buscar(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "rol", required = false) Rol rol,
            @RequestParam(name = "idSucursal", required = false) Integer idSucursal,
            @RequestParam(defaultValue = "0") int page) {
        try {
            return ResponseEntity.ok(usuarioService.buscarPaginado(q, rol, idSucursal, page));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/actualizar/{id}")
    public ResponseEntity<?> actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody UsuarioUpdateRequest request) {
        try {
            return ResponseEntity.ok(usuarioService.actualizar(id, request));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al actualizar usuario" : e.getMessage();
            HttpStatus status = message.toLowerCase().contains("no encontrado")
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    @DeleteMapping("/eliminar/{id}")
    public ResponseEntity<String> eliminar(@PathVariable Integer id) {
        try {
            usuarioService.eliminarLogico(id);
            return ResponseEntity.ok("Usuario eliminado logicamente");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PutMapping("/resetear-password/{id}")
    public ResponseEntity<?> resetearPassword(
            @PathVariable Integer id,
            @Valid @RequestBody UsuarioResetPasswordRequest request) {
        try {
            String message = usuarioService.resetearPassword(id, request);
            return ResponseEntity.ok(Map.of("message", message));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al resetear contrasena" : e.getMessage();
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
