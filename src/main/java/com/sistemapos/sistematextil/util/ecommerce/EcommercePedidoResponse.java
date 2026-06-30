package com.sistemapos.sistematextil.util.ecommerce;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record EcommercePedidoResponse(
        String codigo,
        String estado,
        LocalDateTime reservaExpiraAt,
        BigDecimal total,
        String metodoPago,
        String comprobanteUrl,
        String comprobanteToken,
        List<Detalle> detalles) {

    public record Detalle(
            Integer idProductoVariante,
            String nombreProducto,
            String colorNombre,
            String tallaNombre,
            Integer cantidad,
            BigDecimal precioUnitario,
            BigDecimal subtotal,
            String imagenUrl) {
    }
}
