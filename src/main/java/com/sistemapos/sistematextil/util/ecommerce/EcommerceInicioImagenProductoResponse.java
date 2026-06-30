package com.sistemapos.sistematextil.util.ecommerce;

public record EcommerceInicioImagenProductoResponse(
        Integer idProducto,
        String nombre,
        String slug,
        String imagenUrl,
        String imagenThumbUrl
) {
}
