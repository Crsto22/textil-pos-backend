package com.sistemapos.sistematextil.util.dashboard;

import java.time.LocalDateTime;
import java.util.List;

public record DashboardAlmacenResponse(
        String dashboard,
        String filtro,
        Integer idSucursal,
        String sucursal,
        long variantesAgotadas,
        long stockBajo,
        long totalFisicoEnTienda,
        long variantesDisponibles,
        List<DashboardStockCeroItem> reposicionUrgente,
        List<DashboardTopProductoItem> topMayorSalida,
        ResumenMovimientos resumenMovimientos,
        List<MovimientoRecienteItem> ultimosMovimientos,
        List<StockActualItem> topStockActual) {

    public record ResumenMovimientos(
            long totalMovimientos,
            long unidadesEntrada,
            long unidadesSalida,
            long unidadesAjuste,
            long unidadesReserva,
            long unidadesLiberacion,
            long trasladosEntrada,
            long unidadesTrasladoEntrada,
            long trasladosSalida,
            long unidadesTrasladoSalida) {
    }

    public record MovimientoRecienteItem(
            Integer idHistorial,
            LocalDateTime fecha,
            String tipoMovimiento,
            String motivo,
            Integer idProductoVariante,
            String producto,
            String color,
            String talla,
            Integer cantidad,
            Integer stockAnterior,
            Integer stockNuevo) {
    }

    public record StockActualItem(
            Integer idProductoVariante,
            String producto,
            String color,
            String talla,
            long stockActual) {
    }
}
