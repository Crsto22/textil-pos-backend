package com.sistemapos.sistematextil.services;

import java.util.Locale;

import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.util.sunat.SunatEstado;

@Service
public class SunatErrorClassifierService {

    public SunatEstado classify(String message) {
        if (message == null || message.isBlank()) {
            return SunatEstado.ERROR_DEFINITIVO;
        }

        String normalized = message.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("timeout")
                || normalized.contains("timed out")
                || normalized.contains("no se pudo conectar")
                || normalized.contains("error de conexion")
                || normalized.contains("connection reset")
                || normalized.contains("connection refused")
                || normalized.contains("host")
                || normalized.contains("503")
                || normalized.contains("502")
                || normalized.contains("504")
                || normalized.contains("gateway")
                || normalized.contains("cdr aun no disponible")
                || normalized.contains("consultar cdr manualmente")
                || normalized.contains("sin ticket en respuesta")) {
            return SunatEstado.ERROR_TRANSITORIO;
        }
        return SunatEstado.ERROR_DEFINITIVO;
    }
}
