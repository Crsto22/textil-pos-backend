package com.sistemapos.sistematextil.util.notacredito;

import java.math.BigDecimal;

public record NotaCreditoDetalleItemResponse(
        Integer idNotaCreditoDetalle,
        Integer idVentaDetalleReferencia,
        Integer idProductoVariante,
        Integer idProducto,
        String nombreProducto,
        String descripcion,
        String sku,
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
        BigDecimal totalDetalle) {
}
