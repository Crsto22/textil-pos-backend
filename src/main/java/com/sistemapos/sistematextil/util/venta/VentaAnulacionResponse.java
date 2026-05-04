package com.sistemapos.sistematextil.util.venta;

import java.time.LocalDateTime;

import com.sistemapos.sistematextil.util.sunat.SunatBajaEstado;

public record VentaAnulacionResponse(
        Integer idVenta,
        String numeroVenta,
        String tipoComprobanteVenta,
        String estadoVenta,
        String tipoAnulacion,
        String motivoAnulacion,
        LocalDateTime fechaAnulacion,
        boolean stockDevuelto,
        SunatBajaEstado sunatBajaEstado,
        String sunatBajaCodigo,
        String sunatBajaMensaje,
        String sunatBajaTicket,
        String sunatBajaLote,
        String message) {
}
