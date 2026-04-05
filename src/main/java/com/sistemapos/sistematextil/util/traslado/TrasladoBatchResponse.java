package com.sistemapos.sistematextil.util.traslado;

import java.util.List;

public record TrasladoBatchResponse(
        Integer idSucursalOrigen,
        String nombreSucursalOrigen,
        Integer idSucursalDestino,
        String nombreSucursalDestino,
        String motivo,
        Integer totalItems,
        Integer totalCantidad,
        List<TrasladoResponse> traslados) {
}