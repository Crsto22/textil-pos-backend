package com.sistemapos.sistematextil.util.dashboard;

import java.util.List;

public record DashboardAlmacenResponse(
        String dashboard,
        String filtro,
        long variantesAgotadas,
        long stockBajo,
        long totalFisicoEnTienda,
        long variantesDisponibles,
        List<DashboardStockCeroItem> reposicionUrgente,
        List<DashboardTopProductoItem> topMayorSalida) {
}
