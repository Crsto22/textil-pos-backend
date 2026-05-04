package com.sistemapos.sistematextil.util.cotizacion;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.sistemapos.sistematextil.util.producto.TipoOfertaAplicada;

public record CotizacionDetalleResponse(
        Integer idCotizacionDetalle,
        Integer idProductoVariante,
        Integer idProducto,
        String nombreProducto,
        String sku,
        Double precioOferta,
        LocalDateTime ofertaInicio,
        LocalDateTime ofertaFin,
        BigDecimal precioVigente,
        TipoOfertaAplicada tipoOfertaAplicada,
        Integer sucursalOfertaId,
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
