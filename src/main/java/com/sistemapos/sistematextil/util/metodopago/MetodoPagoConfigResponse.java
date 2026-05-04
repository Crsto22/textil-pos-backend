package com.sistemapos.sistematextil.util.metodopago;

import java.time.LocalDateTime;
import java.util.List;

public record MetodoPagoConfigResponse(
        Integer idMetodoPago,
        String nombre,
        String estado,
        String descripcion,
        List<MetodoPagoCuentaResponse> cuentas,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
}
