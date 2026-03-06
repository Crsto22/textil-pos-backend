package com.sistemapos.sistematextil.util.comprobante;

import java.time.LocalDateTime;

public record ComprobanteConfigResponse(
        Integer idComprobante,
        Integer idSucursal,
        String nombreSucursal,
        String tipoComprobante,
        String serie,
        Integer ultimoCorrelativo,
        Integer siguienteCorrelativo,
        String activo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
}
