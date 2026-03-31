package com.sistemapos.sistematextil.util.venta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record VentaReporteResponse(
        String agrupacion,
        String periodoFiltro,
        LocalDate desde,
        LocalDate hasta,
        Integer idSucursal,
        String nombreSucursal,
        Integer idUsuario,
        String nombreUsuario,
        boolean incluirAnuladas,
        BigDecimal montoTotal,
        long cantidadVentas,
        BigDecimal ticketPromedio,
        List<PeriodoItem> periodos,
        List<DetalleItem> detalleVentas,
        List<ClienteItem> clientes) {

    public record PeriodoItem(
            String periodo,
            long cantidadVentas,
            BigDecimal montoTotal,
            BigDecimal ticketPromedio) {
    }

    public record DetalleItem(
            Integer idVenta,
            LocalDateTime fecha,
            String tipoComprobante,
            String serie,
            Integer correlativo,
            String estado,
            Integer idCliente,
            String nombreCliente,
            String telefonoCliente,
            Integer idUsuario,
            String nombreUsuario,
            Integer idSucursal,
            String nombreSucursal,
            BigDecimal subtotal,
            BigDecimal descuentoTotal,
            BigDecimal igv,
            BigDecimal total) {
    }

    public record ClienteItem(
            Integer idCliente,
            String nombreCliente,
            long cantidadVentas,
            BigDecimal montoTotal,
            BigDecimal ticketPromedio) {
    }
}
