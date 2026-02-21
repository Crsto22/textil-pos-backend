package com.sistemapos.sistematextil.util.producto;

public record ProductoColorResumen(
        Integer colorId,
        String nombre,
        String hex,
        ProductoColorImagenResumen imagenPrincipal,
        java.util.List<ProductoTallaResumen> tallas
) {
}
