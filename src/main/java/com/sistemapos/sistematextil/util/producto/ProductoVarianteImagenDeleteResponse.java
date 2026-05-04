package com.sistemapos.sistematextil.util.producto;

import java.util.List;

public record ProductoVarianteImagenDeleteResponse(
        Integer idProductoVariante,
        Integer idProducto,
        Integer colorId,
        String grupoImagenKey,
        Integer idColorImagenEliminada,
        List<ImagenItem> imagenesRestantes,
        ImagenItem imagenPrincipalActual
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
