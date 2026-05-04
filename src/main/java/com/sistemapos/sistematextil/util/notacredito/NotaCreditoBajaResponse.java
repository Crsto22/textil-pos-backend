package com.sistemapos.sistematextil.util.notacredito;

import java.time.LocalDateTime;

import com.sistemapos.sistematextil.util.sunat.SunatBajaEstado;

public record NotaCreditoBajaResponse(
        Integer idNotaCredito,
        String numeroNotaCredito,
        String tipoComprobanteNotaCredito,
        String estadoNotaCredito,
        String tipoAnulacion,
        String motivoAnulacion,
        LocalDateTime fechaAnulacion,
        boolean stockRevertido,
        SunatBajaEstado sunatBajaEstado,
        String sunatBajaCodigo,
        String sunatBajaMensaje,
        String sunatBajaTicket,
        String sunatBajaLote,
        String message) {
}
