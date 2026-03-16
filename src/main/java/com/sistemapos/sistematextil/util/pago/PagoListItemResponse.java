package com.sistemapos.sistematextil.util.pago;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PagoListItemResponse(
        Integer idPago,
        LocalDateTime fecha,
        BigDecimal monto,
        String codigoOperacion,
        Integer idMetodoPago,
        String metodoPago,
        Integer idVenta,
        String tipoComprobante,
        String serie,
        Integer correlativo,
        Integer idCliente,
        String nombreCliente,
        Integer idUsuario,
        String nombreUsuario,
        Integer idSucursal,
        String nombreSucursal
) {
}
