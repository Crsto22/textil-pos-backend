package com.sistemapos.sistematextil.util.traslado;

import java.time.LocalDateTime;

public record TrasladoResponse(
        Integer idTraslado,
        Integer idSucursalOrigen,
        String nombreSucursalOrigen,
        Integer idSucursalDestino,
        String nombreSucursalDestino,
        Integer idProductoVariante,
        String producto,
        String sku,
        String color,
        String talla,
        Integer cantidad,
        String motivo,
        Integer idUsuario,
        String nombreUsuario,
        LocalDateTime fecha) {
}
