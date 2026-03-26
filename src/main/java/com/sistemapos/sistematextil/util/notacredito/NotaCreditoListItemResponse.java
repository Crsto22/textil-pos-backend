package com.sistemapos.sistematextil.util.notacredito;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.sistemapos.sistematextil.util.sunat.SunatEstado;

public record NotaCreditoListItemResponse(
        Integer idNotaCredito,
        LocalDateTime fecha,
        String tipoComprobante,
        String serie,
        Integer correlativo,
        String moneda,
        BigDecimal total,
        String estado,
        SunatEstado sunatEstado,
        String codigoMotivo,
        String descripcionMotivo,
        boolean stockDevuelto,
        Integer idVentaReferencia,
        String numeroVentaReferencia,
        String tipoComprobanteVentaReferencia,
        Integer idCliente,
        String nombreCliente,
        Integer idUsuario,
        String nombreUsuario,
        Integer idSucursal,
        String nombreSucursal,
        long items) {
}
