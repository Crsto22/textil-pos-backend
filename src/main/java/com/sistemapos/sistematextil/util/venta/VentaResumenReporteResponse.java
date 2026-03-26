package com.sistemapos.sistematextil.util.venta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record VentaResumenReporteResponse(
        String filtro,
        LocalDate desde,
        LocalDate hasta,
        Integer idSucursal,
        String nombreSucursal,
        KpiResumen kpis,
        List<TendenciaDiaItem> tendenciaMontoPorDia,
        List<TipoComprobanteItem> ventasPorTipoComprobante,
        List<EstadoDistribucionItem> distribucionPorEstado,
        List<SucursalVentaItem> ventasPorSucursal) {

    public record KpiResumen(
            BigDecimal ventasDelDia,
            BigDecimal ventasDelMes,
            BigDecimal ticketPromedio,
            long cantidadComprobantes) {
    }

    public record TendenciaDiaItem(
            LocalDate fecha,
            BigDecimal monto) {
    }

    public record TipoComprobanteItem(
            String tipoComprobante,
            long cantidadComprobantes,
            BigDecimal montoVendido) {
    }

    public record EstadoDistribucionItem(
            String estado,
            long cantidadComprobantes,
            BigDecimal montoTotal) {
    }

    public record SucursalVentaItem(
            Integer idSucursal,
            String sucursal,
            long cantidadComprobantes,
            BigDecimal montoVendido) {
    }
}
