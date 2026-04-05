package com.sistemapos.sistematextil.util.historialstock;

import java.time.LocalDateTime;

public record HistorialStockListItemResponse(
        Integer idHistorial,
        LocalDateTime fecha,
        String tipoMovimiento,
        String motivo,
        Integer idProductoVariante,
        Integer idProducto,
        String producto,
        String sku,
        String codigoBarras,
        String color,
        String talla,
        Integer idSucursal,
        String nombreSucursal,
        String tipoSucursal,
        Integer idUsuario,
        String nombreUsuario,
        Integer cantidad,
        Integer stockAnterior,
        Integer stockNuevo) {
}
