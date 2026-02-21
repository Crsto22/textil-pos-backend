package com.sistemapos.sistematextil.util.producto;

import java.time.LocalDateTime;
import java.util.List;

public record ProductoListadoResumenResponse(
        Integer idProducto,
        String sku,
        String nombre,
        String descripcion,
        String estado,
        LocalDateTime fechaCreacion,
        String codigoExterno,
        Double precioMin,
        Double precioMax,
        Integer idCategoria,
        String nombreCategoria,
        Integer idSucursal,
        String nombreSucursal,
        List<ProductoColorResumen> colores
) {
}
