package com.sistemapos.sistematextil.util.ecommerce;

import java.math.BigDecimal;
import java.util.List;

public record EcommerceInicioComboResponse(
        Integer idPromocionCombo,
        String nombre,
        String regla,
        BigDecimal precioCombo,
        BigDecimal precioRegularMinimo,
        BigDecimal ahorroMinimo,
        List<Item> items
) {
    public record Item(
            Integer idProducto,
            String nombre,
            String slug,
            Integer cantidadRequerida,
            String imagenGlobalUrl,
            String imagenGlobalThumbUrl
    ) {
    }
}
