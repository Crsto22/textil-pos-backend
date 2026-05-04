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

import com.sistemapos.sistematextil.services.GuiaRemisionCatalogoService;
import com.sistemapos.sistematextil.util.guiaremision.GuiaRemisionConductorRequest;
import com.sistemapos.sistematextil.util.guiaremision.GuiaRemisionTransportistaRequest;
import com.sistemapos.sistematextil.util.guiaremision.GuiaRemisionVehiculoRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(value = "api/guia-remision/catalogos", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class GuiaRemisionCatalogoController {

    private final GuiaRemisionCatalogoService service;

    @GetMapping("/conductores")
    public ResponseEntity<?> listarConductores(
            Authentication authentication,
            @RequestParam(name = "q", required = false) String q) {
        try {
            return ResponseEntity.ok(service.listarConductores(q, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            return error(e, "Error al listar catalogo de conductores");
        }
    }

    @GetMapping("/conductores/{id}")
    public ResponseEntity<?> obtenerConductor(Authentication authentication, @PathVariable Integer id) {
        try {
            return ResponseEntity.ok(service.obtenerConductor(id, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            return error(e, "Error al obtener conductor");
        }
    }

    @PostMapping("/conductores")
    public ResponseEntity<?> crearConductor(
            Authentication authentication,
            @Valid @RequestBody GuiaRemisionConductorRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(service.insertarConductor(request, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            return error(e, "Error al crear conductor");
        }
    }

    @PutMapping("/conductores/{id}")
    public ResponseEntity<?> actualizarConductor(
            Authentication authentication,
            @PathVariable Integer id,
            @Valid @RequestBody GuiaRemisionConductorRequest request) {
        try {
            return ResponseEntity.ok(service.actualizarConductor(id, request, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            return error(e, "Error al actualizar conductor");
        }
    }

    @DeleteMapping("/conductores/{id}")
    public ResponseEntity<?> eliminarConductor(Authentication authentication, @PathVariable Integer id) {
        try {
            service.eliminarConductor(id, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(Map.of("message", "Conductor eliminado logicamente"));
        } catch (RuntimeException e) {
            return error(e, "Error al eliminar conductor");
        }
    }

    @GetMapping("/transportistas")
    public ResponseEntity<?> listarTransportistas(
            Authentication authentication,
            @RequestParam(name = "q", required = false) String q) {
        try {
            return ResponseEntity.ok(service.listarTransportistas(q, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            return error(e, "Error al listar catalogo de transportistas");
        }
    }

    @GetMapping("/transportistas/{id}")
    public ResponseEntity<?> obtenerTransportista(Authentication authentication, @PathVariable Integer id) {
        try {
            return ResponseEntity.ok(service.obtenerTransportista(id, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            return error(e, "Error al obtener transportista");
        }
    }

    @PostMapping("/transportistas")
    public ResponseEntity<?> crearTransportista(
            Authentication authentication,
            @Valid @RequestBody GuiaRemisionTransportistaRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(service.insertarTransportista(request, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            return error(e, "Error al crear transportista");
        }
    }

    @PutMapping("/transportistas/{id}")
    public ResponseEntity<?> actualizarTransportista(
            Authentication authentication,
            @PathVariable Integer id,
            @Valid @RequestBody GuiaRemisionTransportistaRequest request) {
        try {
            return ResponseEntity.ok(
                    service.actualizarTransportista(id, request, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            return error(e, "Error al actualizar transportista");
        }
    }

    @DeleteMapping("/transportistas/{id}")
    public ResponseEntity<?> eliminarTransportista(Authentication authentication, @PathVariable Integer id) {
        try {
            service.eliminarTransportista(id, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(Map.of("message", "Transportista eliminado logicamente"));
        } catch (RuntimeException e) {
            return error(e, "Error al eliminar transportista");
        }
    }

    @GetMapping("/vehiculos")
    public ResponseEntity<?> listarVehiculos(
            Authentication authentication,
            @RequestParam(name = "q", required = false) String q) {
        try {
            return ResponseEntity.ok(service.listarVehiculos(q, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            return error(e, "Error al listar catalogo de vehiculos");
        }
    }

    @GetMapping("/vehiculos/{id}")
    public ResponseEntity<?> obtenerVehiculo(Authentication authentication, @PathVariable Integer id) {
        try {
            return ResponseEntity.ok(service.obtenerVehiculo(id, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            return error(e, "Error al obtener vehiculo");
        }
    }

    @PostMapping("/vehiculos")
    public ResponseEntity<?> crearVehiculo(
            Authentication authentication,
            @Valid @RequestBody GuiaRemisionVehiculoRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(service.insertarVehiculo(request, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            return error(e, "Error al crear vehiculo");
        }
    }

    @PutMapping("/vehiculos/{id}")
    public ResponseEntity<?> actualizarVehiculo(
            Authentication authentication,
            @PathVariable Integer id,
            @Valid @RequestBody GuiaRemisionVehiculoRequest request) {
        try {
            return ResponseEntity.ok(service.actualizarVehiculo(id, request, obtenerCorreoAutenticado(authentication)));
        } catch (RuntimeException e) {
            return error(e, "Error al actualizar vehiculo");
        }
    }

    @DeleteMapping("/vehiculos/{id}")
    public ResponseEntity<?> eliminarVehiculo(Authentication authentication, @PathVariable Integer id) {
        try {
            service.eliminarVehiculo(id, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(Map.of("message", "Vehiculo eliminado logicamente"));
        } catch (RuntimeException e) {
            return error(e, "Error al eliminar vehiculo");
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

    private ResponseEntity<Map<String, String>> error(RuntimeException e, String fallback) {
        String message = e.getMessage() == null ? fallback : e.getMessage();
        return ResponseEntity.status(resolverStatus(message)).body(Map.of("message", message));
    }

    private HttpStatus resolverStatus(String message) {
        String lower = message.toLowerCase();
        if (lower.contains("no encontrad")) return HttpStatus.NOT_FOUND;
        if (lower.contains("no autenticado") || lower.contains("no tiene permisos")) return HttpStatus.FORBIDDEN;
        if (lower.contains("ya existe")) return HttpStatus.CONFLICT;
        return HttpStatus.BAD_REQUEST;
    }

    private String obtenerCorreoAutenticado(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return authentication.getName();
    }
}
