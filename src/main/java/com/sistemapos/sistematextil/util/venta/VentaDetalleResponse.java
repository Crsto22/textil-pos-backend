package com.sistemapos.sistematextil.util.venta;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.sistemapos.sistematextil.util.producto.TipoOfertaAplicada;

public record VentaDetalleResponse(
        Integer idVentaDetalle,
        Integer idProductoVariante,
        Integer idProducto,
        String nombreProducto,
        String descripcion,
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
        String unidadMedida,
        String codigoTipoAfectacionIgv,
        BigDecimal precioUnitario,
        BigDecimal descuento,
        BigDecimal igvDetalle,
        BigDecimal subtotal,
        BigDecimal totalDetalle
) {
}
