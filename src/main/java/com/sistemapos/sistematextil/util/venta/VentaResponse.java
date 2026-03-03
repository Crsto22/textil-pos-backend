package com.sistemapos.sistematextil.util.venta;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record VentaResponse(
        Integer idVenta,
        LocalDateTime fecha,
        String tipoComprobante,
        String serie,
        Integer correlativo,
        BigDecimal igvPorcentaje,
        BigDecimal subtotal,
        BigDecimal descuentoTotal,
        String tipoDescuento,
        BigDecimal igv,
        BigDecimal total,
        String estado,
        Integer idCliente,
        String nombreCliente,
        Integer idUsuario,
        String nombreUsuario,
        Integer idSucursal,
        String nombreSucursal,
        List<VentaDetalleResponse> detalles,
        List<VentaPagoResponse> pagos
) {
}
