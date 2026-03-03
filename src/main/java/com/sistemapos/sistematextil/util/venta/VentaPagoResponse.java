package com.sistemapos.sistematextil.util.venta;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VentaPagoResponse(
        Integer idPago,
        Integer idMetodoPago,
        String metodoPago,
        BigDecimal monto,
        String referencia,
        LocalDateTime fecha
) {
}
