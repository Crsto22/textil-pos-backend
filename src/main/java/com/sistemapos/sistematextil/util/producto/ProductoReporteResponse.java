package com.sistemapos.sistematextil.util.producto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProductoReporteResponse(
        String filtro,
        LocalDate desde,
        LocalDate hasta,
        Integer idSucursal,
        String nombreSucursal,
        KpiResumen kpis,
        List<TopProductoItem> topProductosPorMonto,
        List<TopProductoItem> topProductosPorUnidades,
        List<HeatmapTallaColorItem> heatmapVentasPorTallaColor,
        List<CategoriaVentaItem> ventasPorCategoria) {

    public record KpiResumen(
            long productosActivos,
            long variantesActivas,
            long variantesSinStock,
            BigDecimal rotacionPromedio) {
    }

    public record TopProductoItem(
            Integer idProducto,
            String producto,
            Integer idProductoVariante,
            String variante,
            String color,
            String talla,
            Integer idSucursal,
            String nombreSucursal,
            long unidadesVendidas,
            BigDecimal montoVendido) {
    }

    public record HeatmapTallaColorItem(
            Integer idColor,
            String color,
            String codigoColor,
            Integer idTalla,
            String talla,
            long unidadesVendidas,
            BigDecimal montoVendido) {
    }

    public record CategoriaVentaItem(
            Integer idCategoria,
            String categoria,
            long unidadesVendidas,
            BigDecimal montoVendido) {
    }
}
