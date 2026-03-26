package com.sistemapos.sistematextil.util.cliente;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ClienteReporteResponse(
        String filtro,
        LocalDate desde,
        LocalDate hasta,
        Integer idSucursal,
        String nombreSucursal,
        KpiResumen kpis,
        List<ClienteRankingItem> topClientesPorMonto,
        List<ClienteRankingItem> topClientesPorCompras,
        List<CohorteSemanalItem> cohorteSemanal,
        List<RfmClienteItem> segmentacionRfm) {

    public record KpiResumen(
            long clientesActivos,
            long clientesNuevosMes,
            BigDecimal recurrenciaPct) {
    }

    public record ClienteRankingItem(
            Integer idCliente,
            String cliente,
            String tipoDocumento,
            String nroDocumento,
            long compras,
            BigDecimal totalGastado,
            BigDecimal ticketPromedio,
            LocalDateTime ultimaCompra) {
    }

    public record CohorteSemanalItem(
            String cohorteSemana,
            LocalDate inicioSemana,
            long clientesNuevos,
            long clientesQueRecompran,
            BigDecimal tasaRecompraPct) {
    }

    public record RfmClienteItem(
            Integer idCliente,
            String cliente,
            String tipoDocumento,
            String nroDocumento,
            LocalDateTime ultimaCompra,
            Integer recenciaDias,
            long frecuencia,
            BigDecimal monto) {
    }

}
