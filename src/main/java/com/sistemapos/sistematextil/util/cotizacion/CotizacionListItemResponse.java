package com.sistemapos.sistematextil.util.cotizacion;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CotizacionListItemResponse(
        Integer idCotizacion,
        LocalDateTime fecha,
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
        long items
) {
}
