package com.sistemapos.sistematextil.util.comprobante;

import java.time.LocalDateTime;

public record ComprobanteConfigResponse(
        Integer idComprobante,
        String tipoComprobante,
        String serie,
        Integer ultimoCorrelativo,
        Integer siguienteCorrelativo,
        String activo,
        Boolean habilitadoVenta,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
}
