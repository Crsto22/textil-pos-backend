package com.sistemapos.sistematextil.util.cliente;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ClienteCompraResumenResponse(
        Integer idVenta,
        LocalDateTime fecha,
        String tipoComprobante,
        String serie,
        Integer correlativo,
        String moneda,
        BigDecimal total,
        String estado
) {
}
