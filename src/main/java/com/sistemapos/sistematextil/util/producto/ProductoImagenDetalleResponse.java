package com.sistemapos.sistematextil.util.producto;

public record ProductoImagenDetalleResponse(
        Integer idColorImagen,
        Integer colorId,
        String colorNombre,
        String colorHex,
        String url,
        String urlThumb,
        Integer orden,
        Boolean esPrincipal,
        String estado
) {
}
