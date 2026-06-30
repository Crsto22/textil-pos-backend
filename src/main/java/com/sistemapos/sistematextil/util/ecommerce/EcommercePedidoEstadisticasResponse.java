package com.sistemapos.sistematextil.util.ecommerce;

import java.math.BigDecimal;

public record EcommercePedidoEstadisticasResponse(
        long completados,
        long vencidos,
        long enProgreso,
        BigDecimal gananciasCompletadas
) {
}
