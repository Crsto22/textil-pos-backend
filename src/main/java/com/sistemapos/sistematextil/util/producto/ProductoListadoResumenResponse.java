package com.sistemapos.sistematextil.util.producto;

import java.time.LocalDateTime;
import java.util.List;

public record ProductoListadoResumenResponse(
        Integer idProducto,
        String sku,
        String nombre,
        String slug,
        String descripcion,
        String imagenGlobalUrl,
        String imagenGlobalThumbUrl,
        Boolean publicarEcommerce,
        String estado,
        LocalDateTime fechaCreacion,
        Double precioMin,
        Double precioMax,
        Integer idCategoria,
        String nombreCategoria,
        Integer idSucursal,
        String nombreSucursal,
        List<ProductoColorResumen> colores
) {
}
