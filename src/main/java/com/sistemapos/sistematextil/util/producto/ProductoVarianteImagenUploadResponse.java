package com.sistemapos.sistematextil.util.producto;

import java.util.List;

public record ProductoVarianteImagenUploadResponse(
        Integer idProductoVariante,
        Integer idProducto,
        Integer colorId,
        String grupoImagenKey,
        List<ImagenItem> imagenes
) {
    public record ImagenItem(
            Integer idColorImagen,
            String url,
            String urlThumb,
            Integer orden,
            Boolean esPrincipal
    ) {
    }
}
