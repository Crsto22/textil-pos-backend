package com.sistemapos.sistematextil.controllers;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.services.AnulacionService;
import com.sistemapos.sistematextil.services.SunatBajaEmissionService;
import com.sistemapos.sistematextil.util.anulacion.AnulacionInfoResponse;
import com.sistemapos.sistematextil.util.anulacion.AnulacionRequest;
import com.sistemapos.sistematextil.util.anulacion.AnulacionResponse;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(value = "api/anulacion", produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class AnulacionController {

    private final AnulacionService anulacionService;
    private final SunatBajaEmissionService sunatBajaEmissionService;

    /**
     * GET /api/anulacion/verificar/{idVenta}
     * Pre-verifica si una venta se puede anular y qué método corresponde.
     */
    @GetMapping("/verificar/{idVenta}")
    public ResponseEntity<?> verificar(
            Authentication authentication,
            @PathVariable Integer idVenta) {
        try {
            AnulacionInfoResponse response = anulacionService
                    .verificarAnulacion(idVenta, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al verificar anulación" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    /**
     * POST /api/anulacion/anular
     * Ejecuta la anulación. Determina automáticamente el método:
     * - NOTA DE VENTA → Anulación interna
     * - BOLETA/FACTURA ≤7 días → Comunicación de Baja
     * - BOLETA/FACTURA >7 días → Nota de Crédito
     */
    @PostMapping("/anular")
    public ResponseEntity<?> anular(
            Authentication authentication,
            @Valid @RequestBody AnulacionRequest request) {
        try {
            AnulacionResponse response = anulacionService
                    .anular(request, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al anular venta" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    /**
     * POST /api/anulacion/baja/{idBaja}/confirmar
     * Confirma una comunicación de baja tras recibir respuesta de SUNAT.
     * Si SUNAT aceptó → revierte stock y marca venta como ANULADA.
     * Si SUNAT rechazó → restaura la venta a estado EMITIDA.
     */
    @PostMapping("/baja/{idBaja}/confirmar")
    public ResponseEntity<?> confirmarBaja(
            Authentication authentication,
            @PathVariable Integer idBaja) {
        try {
            AnulacionResponse response = anulacionService
                    .confirmarBaja(idBaja, obtenerCorreoAutenticado(authentication));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al confirmar baja" : e.getMessage();
            HttpStatus status = resolverStatus(message, HttpStatus.BAD_REQUEST);
            return ResponseEntity.status(status).body(Map.of("message", message));
        }
    }

    /**
     * POST /api/anulacion/baja/tickets/consultar
     * Consulta masivamente el estado de todos los tickets de baja pendientes en SUNAT.
     */
    @PostMapping("/baja/tickets/consultar")
    public ResponseEntity<?> consultarTickets(Authentication authentication) {
        try {
            obtenerCorreoAutenticado(authentication);
            int procesados = sunatBajaEmissionService.consultarTicketsPendientes();
            return ResponseEntity.ok(Map.of(
                    "message", "Tickets procesados: " + procesados,
                    "ticketsProcesados", procesados));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Error al consultar tickets" : e.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
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
