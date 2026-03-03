package com.sistemapos.sistematextil.util.venta;

import java.math.BigDecimal;

public record VentaDetalleResponse(
        Integer idVentaDetalle,
        Integer idProductoVariante,
        Integer idProducto,
        String nombreProducto,
        String sku,
        String codigoExterno,
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
