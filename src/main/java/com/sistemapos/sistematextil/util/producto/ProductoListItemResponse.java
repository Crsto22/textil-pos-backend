package com.sistemapos.sistematextil.util.producto;

import java.time.LocalDateTime;

public record ProductoListItemResponse(
        Integer idProducto,
        String sku,
        String nombre,
        String descripcion,
        String estado,
        LocalDateTime fechaCreacion,
        String codigoExterno,
        Integer idCategoria,
        String nombreCategoria,
        Integer idSucursal,
        String nombreSucursal
) {
}
