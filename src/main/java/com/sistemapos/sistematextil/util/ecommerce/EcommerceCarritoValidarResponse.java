package com.sistemapos.sistematextil.util.ecommerce;

import java.math.BigDecimal;
import java.util.List;

public record EcommerceCarritoValidarResponse(
        boolean valido,
        List<Item> items,
        EcommerceCarritoResumenResponse resumen,
        List<PromocionNoDisponible> promocionesNoDisponibles
) {
    public record Item(
            Integer idProductoVariante,
            String nombre,
            Integer cantidadSolicitada,
            Integer cantidadPermitida,
            boolean cantidadValida,
            Integer stock,
            boolean disponible,
            BigDecimal precioVigente,
            boolean precioCambiado,
            String mensaje
    ) {
    }

    public record PromocionNoDisponible(
            Integer idPromocionCombo,
            String nombre
    ) {
    }
}
