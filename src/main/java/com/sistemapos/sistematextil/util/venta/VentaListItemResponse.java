package com.sistemapos.sistematextil.util.venta;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.sistemapos.sistematextil.util.sunat.SunatEstado;

public record VentaListItemResponse(
        Integer idVenta,
        LocalDateTime fecha,
        String tipoComprobante,
        String serie,
        Integer correlativo,
        String moneda,
        BigDecimal total,
        String estado,
        SunatEstado sunatEstado,
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
