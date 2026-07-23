package com.sistemapos.sistematextil.controllers;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sistemapos.sistematextil.services.AsistenciaService;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(value = "/iclock", produces = MediaType.TEXT_PLAIN_VALUE)
@RequiredArgsConstructor
public class ZktecoAdmsController {

    private static final Logger log = LoggerFactory.getLogger(ZktecoAdmsController.class);
    private final AsistenciaService asistenciaService;

    @GetMapping("/cdata")
    public String opciones(@RequestParam(name = "SN") String serial) {
        return asistenciaService.opcionesAdms(serial);
    }

    @PostMapping("/cdata")
    public String recibir(
            @RequestParam(name = "SN") String serial,
            @RequestParam(required = false) String table,
            @RequestBody(required = false) String body) {
        int aceptadas = asistenciaService.recibirAdms(serial, table, body);
        return "OK: " + aceptadas;
    }

    @GetMapping("/getrequest")
    public String consultarComandos(@RequestParam(name = "SN") String serial) {
        asistenciaService.registrarConsultaAdms(serial);
        return "OK";
    }

    @PostMapping("/devicecmd")
    public String confirmarComando(
            @RequestParam(name = "SN") String serial,
            @RequestBody(required = false) String body) {
        asistenciaService.validarTamanoAdms(body);
        asistenciaService.registrarConsultaAdms(serial);
        return "OK";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleAdmsError(IllegalArgumentException ex, HttpServletRequest request) {
        String serial = request.getParameter("SN");
        String serialOculto = serial == null || serial.isBlank() ? "sin-serial"
                : serial.length() <= 4 ? "****" : "****" + serial.substring(serial.length() - 4);
        log.warn("ADMS rechazado ip={} serial={} motivo={}", request.getRemoteAddr(), serialOculto, ex.getMessage());
        return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("ERROR: " + ex.getMessage());
    }
}
