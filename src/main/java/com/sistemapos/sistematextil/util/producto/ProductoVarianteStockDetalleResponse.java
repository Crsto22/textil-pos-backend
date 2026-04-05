package com.sistemapos.sistematextil.util.producto;

public record ProductoVarianteStockDetalleResponse(
        Integer idSucursal,
        String nombreSucursal,
        String tipoSucursal,
        Integer cantidad
) {
}
