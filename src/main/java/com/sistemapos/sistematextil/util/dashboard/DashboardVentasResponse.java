package com.sistemapos.sistematextil.util.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DashboardVentasResponse(
        String dashboard,
        String filtro,
        LocalDate desde,
        LocalDate hasta,
        BigDecimal misVentasTotales,
        long misProductosVendidos,
        long misCotizacionesAbiertas,
        BigDecimal miPromedioVenta,
        List<DashboardSerieItem> misVentasPorFecha,
        List<DashboardTopProductoItem> topProductosMasVendidosGenerales) {
}
