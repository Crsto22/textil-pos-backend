package com.sistemapos.sistematextil.util.producto;

public record ProductoColorImagenResumen(
        String url,
        String urlThumb,
        Integer orden,
        Boolean esPrincipal
) {
}
