package com.sistemapos.sistematextil.util.producto;

import java.time.LocalDateTime;

public record ProductoVariantePosResponse(
        Integer idProductoVariante,
        Integer idSucursal,
        String codigoBarras,
        String sku,
        Integer stock,
        String estado,
        Double precio,
        Double precioMayor,
        Double precioOferta,
        LocalDateTime ofertaInicio,
        LocalDateTime ofertaFin,
        Double precioVigente,
        ProductoItem producto,
        ColorItem color,
        TallaItem talla,
        ImagenItem imagenPrincipal) {

    public record ProductoItem(
            Integer idProducto,
            String nombre,
            String descripcion) {
    }

    public record ColorItem(
            Integer idColor,
            String nombre,
            String hex) {
    }

    public record TallaItem(
            Integer idTalla,
            String nombre) {
    }

    public record ImagenItem(
            String url,
            String urlThumb) {
    }
}
