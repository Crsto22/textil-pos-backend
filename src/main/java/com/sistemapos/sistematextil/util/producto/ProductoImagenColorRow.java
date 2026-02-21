package com.sistemapos.sistematextil.util.producto;

public record ProductoImagenColorRow(
        Integer productoId,
        Integer colorId,
        String colorNombre,
        String colorHex,
        String url,
        String urlThumb,
        Integer orden,
        Boolean esPrincipal
) {
}
