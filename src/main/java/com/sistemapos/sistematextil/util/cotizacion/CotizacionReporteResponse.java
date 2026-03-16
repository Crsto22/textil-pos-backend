package com.sistemapos.sistematextil.util.cotizacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CotizacionReporteResponse(
        String agrupacion,
        String periodoFiltro,
        LocalDate desde,
        LocalDate hasta,
        Integer idSucursal,
        String nombreSucursal,
        String estadoFiltro,
        BigDecimal montoTotal,
        long cantidadCotizaciones,
        BigDecimal ticketPromedio,
        List<PeriodoItem> periodos,
        List<DetalleItem> detalleCotizaciones,
        List<ClienteItem> clientes) {

    public record PeriodoItem(
            String periodo,
            long cantidadCotizaciones,
            BigDecimal montoTotal,
            BigDecimal ticketPromedio) {
    }

    public record DetalleItem(
            Integer idCotizacion,
            LocalDateTime fecha,
            String serie,
            Integer correlativo,
            String estado,
            Integer idCliente,
            String nombreCliente,
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
            long cantidadCotizaciones,
            BigDecimal montoTotal,
            BigDecimal ticketPromedio) {
    }
}
