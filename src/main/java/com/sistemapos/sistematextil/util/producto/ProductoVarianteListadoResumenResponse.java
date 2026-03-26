package com.sistemapos.sistematextil.util.producto;

import java.time.LocalDateTime;
import java.util.List;

public record ProductoVarianteListadoResumenResponse(
        Integer idProductoVariante,
        String sku,
        String codigoBarras,
        String estado,
        Integer stock,
        Double precio,
        Double precioMayor,
        Double precioOferta,
        LocalDateTime ofertaInicio,
        LocalDateTime ofertaFin,
        Double precioVigente,
        ProductoItem producto,
        ColorItem color,
        TallaItem talla,
        ImagenItem imagenPrincipal,
        List<ImagenItem> imagenes
) {
    public record ProductoItem(
            Integer idProducto,
            String nombre,
            String descripcion,
            String estado,
            LocalDateTime fechaCreacion,
            CategoriaItem categoria,
            SucursalItem sucursal
    ) {
    }

    public record CategoriaItem(
            Integer idCategoria,
            String nombreCategoria
    ) {
    }

    public record SucursalItem(
            Integer idSucursal,
            String nombreSucursal
    ) {
    }

    public record ColorItem(
            Integer idColor,
            String nombre,
            String hex
    ) {
    }

    public record TallaItem(
            Integer idTalla,
            String nombre
    ) {
    }

    public record ImagenItem(
            Integer idColorImagen,
            String url,
            String urlThumb,
            Integer orden,
            Boolean esPrincipal,
            String estado
    ) {
    }
}
