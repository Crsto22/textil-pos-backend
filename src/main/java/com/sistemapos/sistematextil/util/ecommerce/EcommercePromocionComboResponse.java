package com.sistemapos.sistematextil.util.ecommerce;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record EcommercePromocionComboResponse(
        Integer idPromocionCombo,
        String nombre,
        String regla,
        BigDecimal precioCombo,
        String estado,
        LocalDateTime fechaInicio,
        LocalDateTime fechaFin,
        Integer usuarioCreacionId,
        String usuarioCreacionNombre,
        List<Item> items
) {
    public record Item(
            Integer idProducto,
            String productoNombre,
            Integer cantidadRequerida
    ) {
    }
}
