package com.sistemapos.sistematextil.util.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DashboardSerieItem(
        LocalDate fecha,
        BigDecimal monto) {
}
