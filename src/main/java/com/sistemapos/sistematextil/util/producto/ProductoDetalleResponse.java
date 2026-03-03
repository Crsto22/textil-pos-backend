package com.sistemapos.sistematextil.util.producto;

import java.util.List;

public record ProductoDetalleResponse(
        ProductoListItemResponse producto,
        List<ProductoVarianteDetalleResponse> variantes,
        List<ProductoImagenDetalleResponse> imagenes
) {
}
