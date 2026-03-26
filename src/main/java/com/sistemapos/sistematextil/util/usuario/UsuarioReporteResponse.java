package com.sistemapos.sistematextil.util.usuario;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record UsuarioReporteResponse(
        String filtro,
        LocalDate desde,
        LocalDate hasta,
        Integer idSucursal,
        String nombreSucursal,
        List<UsuarioKpiItem> kpisPorUsuario,
        List<UsuarioKpiItem> rankingPorMonto,
        List<UsuarioKpiItem> rankingPorComprobantes,
        List<ControlAnulacionItem> controlAnulacionesPorUsuario,
        List<EvolucionUsuarioSerie> evolucionDiariaPorUsuario) {

    public record UsuarioKpiItem(
            Integer idUsuario,
            String usuario,
            String rol,
            long ventas,
            BigDecimal monto,
            BigDecimal ticketPromedio) {
    }

    public record ControlAnulacionItem(
            Integer idUsuario,
            String usuario,
            String rol,
            long anulaciones,
            BigDecimal montoAnulado) {
    }

    public record EvolucionUsuarioSerie(
            Integer idUsuario,
            String usuario,
            String rol,
            List<PuntoDiarioItem> puntos) {
    }

    public record PuntoDiarioItem(
            LocalDate fecha,
            long ventas,
            BigDecimal monto,
            long anulaciones,
            BigDecimal montoAnulado) {
    }
}
