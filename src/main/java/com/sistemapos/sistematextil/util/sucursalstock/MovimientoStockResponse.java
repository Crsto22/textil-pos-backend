package com.sistemapos.sistematextil.util.sucursalstock;

import java.time.LocalDateTime;

public record MovimientoStockResponse(
        Integer idHistorial,
        LocalDateTime fecha,
        String tipoMovimiento,
        String motivo,
        Integer idSucursal,
        String nombreSucursal,
        Integer idProductoVariante,
        Integer idProducto,
        String producto,
        String sku,
        String color,
        String talla,
        Integer cantidadMovimiento,
        Integer stockAnterior,
        Integer stockNuevo) {
}
