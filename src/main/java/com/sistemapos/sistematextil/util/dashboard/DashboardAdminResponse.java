package com.sistemapos.sistematextil.util.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DashboardAdminResponse(
        String dashboard,
        String filtro,
        Integer idSucursal,
        String nombreSucursal,
        LocalDate desde,
        LocalDate hasta,
        BigDecimal ventasTotales,
        long productosVendidos,
        long ticketsEmitidos,
        KpiResumen kpis,
        List<DashboardIngresoMetodoPagoItem> ingresosPorMetodoPago,
        List<DashboardSerieItem> ventasPorFecha,
        List<DashboardTopProductoItem> topProductosMasVendidos,
        List<ComprobanteTipoItem> comprobantesPorTipo,
        List<EstadoVentaItem> distribucionPorEstado,
        List<SucursalVentaItem> ventasPorSucursal,
        StockCriticoResumen stockCritico) {

    public record KpiResumen(
            BigDecimal ventasTotalesFiltro,
            BigDecimal ventasDelDia,
            BigDecimal ventasDelMes,
            BigDecimal ticketPromedio,
            long comprobantesEmitidos,
            long comprobantesAnulados,
            BigDecimal montoAnulado,
            long unidadesVendidas,
            long variantesVendidas) {
    }

    public record ComprobanteTipoItem(
            String tipoComprobante,
            long cantidadComprobantes,
            BigDecimal montoVendido) {
    }

    public record EstadoVentaItem(
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

    public record StockCriticoResumen(
            long variantesAgotadas,
            long stockBajo,
            List<DashboardStockCeroItem> agotados,
            List<DashboardStockCeroItem> prontosAgotarse) {
    }
}
