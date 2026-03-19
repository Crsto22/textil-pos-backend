package com.sistemapos.sistematextil.util.dashboard;

import java.math.BigDecimal;

public record DashboardIngresoMetodoPagoItem(
        String metodoPago,
        BigDecimal monto) {
}
