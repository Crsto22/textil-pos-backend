package com.sistemapos.sistematextil.util.cotizacion;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CotizacionDetalleResponse(
        Integer idCotizacionDetalle,
        Integer idProductoVariante,
        Integer idProducto,
        String nombreProducto,
        String sku,
        Double precioOferta,
        LocalDateTime ofertaInicio,
        LocalDateTime ofertaFin,
        Integer idColor,
        String color,
        Integer idTalla,
        String talla,
        Integer cantidad,
        BigDecimal precioUnitario,
        BigDecimal descuento,
        BigDecimal subtotal
) {
}
