package com.sistemapos.sistematextil.util.venta;

import java.time.LocalDateTime;

import com.sistemapos.sistematextil.util.sunat.SunatEstado;

public record VentaAnulacionResponse(
        Integer idVenta,
        String numeroVenta,
        String tipoComprobanteVenta,
        String estadoVenta,
        String tipoAnulacion,
        String motivoAnulacion,
        LocalDateTime fechaAnulacion,
        boolean stockDevuelto,
        Integer idNotaCredito,
        String numeroNotaCredito,
        String tipoComprobanteNotaCredito,
        SunatEstado sunatEstadoNotaCredito,
        String sunatCodigoNotaCredito,
        String sunatMensajeNotaCredito,
        String message) {
}
