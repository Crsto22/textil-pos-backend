package com.sistemapos.sistematextil.util.notacredito;

import java.time.LocalDateTime;

import com.sistemapos.sistematextil.util.sunat.SunatEstado;

public record NotaCreditoResponse(
        Integer idVenta,
        String numeroVenta,
        String tipoComprobanteVenta,
        String estadoVenta,
        Integer idNotaCredito,
        String numeroNotaCredito,
        String tipoComprobanteNotaCredito,
        String codigoMotivo,
        String descripcionMotivo,
        LocalDateTime fechaNotaCredito,
        boolean stockDevuelto,
        SunatEstado sunatEstadoNotaCredito,
        String sunatCodigoNotaCredito,
        String sunatMensajeNotaCredito,
        String message) {
}
