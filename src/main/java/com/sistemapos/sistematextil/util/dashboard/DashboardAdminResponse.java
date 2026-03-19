package com.sistemapos.sistematextil.util.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DashboardAdminResponse(
        String dashboard,
        String filtro,
        LocalDate desde,
        LocalDate hasta,
        BigDecimal ventasTotales,
        long productosVendidos,
        long ticketsEmitidos,
        List<DashboardIngresoMetodoPagoItem> ingresosPorMetodoPago,
        List<DashboardSerieItem> ventasPorFecha,
        List<DashboardTopProductoItem> topProductosMasVendidos) {
}
