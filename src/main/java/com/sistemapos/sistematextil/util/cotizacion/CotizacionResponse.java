package com.sistemapos.sistematextil.util.cotizacion;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CotizacionResponse(
        Integer idCotizacion,
        LocalDateTime fecha,
        String serie,
        Integer correlativo,
        BigDecimal igvPorcentaje,
        BigDecimal subtotal,
        BigDecimal descuentoTotal,
        String tipoDescuento,
        BigDecimal igv,
        BigDecimal total,
        String estado,
        String observacion,
        Integer idCliente,
        String nombreCliente,
        Integer idUsuario,
        String nombreUsuario,
        Integer idSucursal,
        String nombreSucursal,
        List<CotizacionDetalleResponse> detalles
) {
}
