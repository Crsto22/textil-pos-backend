package com.sistemapos.sistematextil.util.producto;

public record ProductoCompletoResponse(
        ProductoListItemResponse producto,
        int variantes,
        int imagenes
) {
}
