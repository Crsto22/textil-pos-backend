package com.sistemapos.sistematextil.util.venta;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VentaListItemResponse(
        Integer idVenta,
        LocalDateTime fecha,
        String tipoComprobante,
        String serie,
        Integer correlativo,
        BigDecimal total,
        String estado,
        Integer idCliente,
        String nombreCliente,
        Integer idUsuario,
        String nombreUsuario,
        Integer idSucursal,
        String nombreSucursal,
        long items,
        long pagos
) {
}
