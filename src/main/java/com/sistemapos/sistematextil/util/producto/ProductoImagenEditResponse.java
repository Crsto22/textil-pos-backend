package com.sistemapos.sistematextil.util.producto;

public record ProductoImagenEditResponse(
        Integer idColorImagen,
        Integer idProducto,
        Integer colorId,
        String url,
        String urlThumb,
        Integer orden,
        Boolean esPrincipal
) {
}
