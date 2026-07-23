package com.sistemapos.sistematextil.controllers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.services.AsistenciaService;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.DispositivoRequest;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.CargoEstadoRequest;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.CargoRequest;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.TrabajadorRequest;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.MarcacionManualRequest;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.AnularMarcacionRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AsistenciaAdminController {

    private final AsistenciaService asistenciaService;

    @GetMapping("/api/trabajadores")
    public ResponseEntity<?> listarTrabajadores(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer idSucursal,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String modalidad,
            @RequestParam(required = false) Boolean rotativo,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(asistenciaService.listarTrabajadores(
                q, idSucursal, estado, modalidad, rotativo, page));
    }

    @PostMapping("/api/trabajadores")
    public ResponseEntity<?> crearTrabajador(@Valid @RequestBody TrabajadorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(asistenciaService.crearTrabajador(request));
    }

    @PutMapping("/api/trabajadores/{id}")
    public ResponseEntity<?> actualizarTrabajador(
            @PathVariable Integer id, @Valid @RequestBody TrabajadorRequest request) {
        return ResponseEntity.ok(asistenciaService.actualizarTrabajador(id, request));
    }

    @DeleteMapping("/api/trabajadores/{id}")
    public ResponseEntity<?> eliminarTrabajador(@PathVariable Integer id) {
        asistenciaService.eliminarTrabajador(id);
        return ResponseEntity.ok(Map.of("message", "Trabajador eliminado logicamente"));
    }

    @GetMapping("/api/asistencia/cargos")
    public ResponseEntity<?> listarCargos(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(asistenciaService.listarCargos(q, estado, page));
    }

    @PostMapping("/api/asistencia/cargos")
    public ResponseEntity<?> crearCargo(@Valid @RequestBody CargoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(asistenciaService.crearCargo(request));
    }

    @PutMapping("/api/asistencia/cargos/{id}")
    public ResponseEntity<?> actualizarCargo(@PathVariable Integer id, @Valid @RequestBody CargoRequest request) {
        return ResponseEntity.ok(asistenciaService.actualizarCargo(id, request));
    }

    @PatchMapping("/api/asistencia/cargos/{id}/estado")
    public ResponseEntity<?> actualizarEstadoCargo(
            @PathVariable Integer id, @Valid @RequestBody CargoEstadoRequest request) {
        return ResponseEntity.ok(asistenciaService.actualizarEstadoCargo(id, request));
    }

    @GetMapping("/api/dispositivos-asistencia")
    public ResponseEntity<?> listarDispositivos(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer idSucursal,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(asistenciaService.listarDispositivos(q, idSucursal, estado, page));
    }

    @PostMapping("/api/dispositivos-asistencia")
    public ResponseEntity<?> crearDispositivo(@Valid @RequestBody DispositivoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(asistenciaService.crearDispositivo(request));
    }

    @PutMapping("/api/dispositivos-asistencia/{id}")
    public ResponseEntity<?> actualizarDispositivo(
            @PathVariable Integer id, @Valid @RequestBody DispositivoRequest request) {
        return ResponseEntity.ok(asistenciaService.actualizarDispositivo(id, request));
    }

    @GetMapping("/api/asistencia/marcaciones")
    public ResponseEntity<?> listarMarcaciones(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @RequestParam(required = false) Integer idTrabajador,
            @RequestParam(required = false) Integer idSucursal,
            @RequestParam(required = false) Integer idDispositivo,
            @RequestParam(required = false) String vinculacion,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(asistenciaService.listarMarcaciones(
                desde, hasta, idTrabajador, idSucursal, idDispositivo, vinculacion, page));
    }

    @PostMapping("/api/asistencia/marcaciones/manuales")
    public ResponseEntity<?> registrarMarcacionManual(
            Authentication authentication, @Valid @RequestBody MarcacionManualRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(asistenciaService.registrarMarcacionManual(request, authentication.getName()));
    }

    @PostMapping("/api/asistencia/marcaciones/{id}/anular")
    public ResponseEntity<?> anularMarcacion(
            Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody AnularMarcacionRequest request) {
        return ResponseEntity.ok(asistenciaService.anularMarcacion(id, request, authentication.getName()));
    }

    @GetMapping("/api/asistencia/resumen")
    public ResponseEntity<?> obtenerResumen(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer idTrabajador,
            @RequestParam(required = false) Integer idSucursal,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String modalidad,
            @RequestParam(required = false) Boolean rotativo,
            @RequestParam(defaultValue = "DIARIO") String vista,
            @RequestParam(defaultValue = "0") int page) {
        if ("SEMANAL".equalsIgnoreCase(vista)) {
            return ResponseEntity.ok(asistenciaService.obtenerResumenSemanal(
                    desde, hasta, idTrabajador, idSucursal, q, estado, modalidad, rotativo, page));
        }
        return ResponseEntity.ok(asistenciaService.obtenerResumen(
                desde, hasta, idTrabajador, idSucursal, q, estado, modalidad, rotativo, page));
    }

    @GetMapping("/api/asistencia/analisis")
    public ResponseEntity<?> obtenerAnalisis(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer idTrabajador,
            @RequestParam(required = false) Integer idSucursal,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(asistenciaService.obtenerAnalisis(
                desde, hasta, idTrabajador, idSucursal, page));
    }

    @GetMapping(
            value = "/api/asistencia/resumen/reporte/excel",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportarResumenExcel(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer idTrabajador,
            @RequestParam(required = false) Integer idSucursal,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String modalidad,
            @RequestParam(required = false) Boolean rotativo) {
        byte[] archivo = asistenciaService.exportarResumenExcel(
                desde, hasta, idTrabajador, idSucursal, q, estado, modalidad, rotativo, authentication.getName());
        String nombre = "asistencia_" + desde + "_" + hasta + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(archivo);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst().map(error -> error.getDefaultMessage() != null
                        ? error.getDefaultMessage() : "Datos invalidos")
                .orElse("Datos invalidos");
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}
