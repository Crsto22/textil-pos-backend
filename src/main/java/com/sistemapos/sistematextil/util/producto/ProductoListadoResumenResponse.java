package com.sistemapos.sistematextil.util.producto;

import java.time.LocalDate;
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
        String guiaTallasUrl,
        String guiaTallasThumbUrl,
        Boolean publicarEcommerce,
        Boolean preventa,
        LocalDate fechaEnvioPreventa,
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
