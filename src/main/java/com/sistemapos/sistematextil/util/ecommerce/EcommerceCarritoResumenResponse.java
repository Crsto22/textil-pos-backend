package com.sistemapos.sistematextil.util.ecommerce;

import java.math.BigDecimal;
import java.util.List;

public record EcommerceCarritoResumenResponse(
        BigDecimal subtotal,
        BigDecimal descuentoPromocion,
        BigDecimal total,
        List<ComboAplicado> combosAplicados,
        List<ComboPendiente> combosPendientes
) {
    public record ComboAplicado(
            Integer idPromocionCombo,
            String nombre,
            String regla,
            BigDecimal precioNormal,
            BigDecimal precioCombo,
            BigDecimal descuento
    ) {
    }

    public record ComboPendiente(
            Integer idPromocionCombo,
            String nombre,
            String regla,
            String mensaje
    ) {
    }
}
