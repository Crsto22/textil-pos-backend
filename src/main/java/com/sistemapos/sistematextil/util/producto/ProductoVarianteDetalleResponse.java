package com.sistemapos.sistematextil.util.producto;

import java.time.LocalDateTime;

public record ProductoVarianteDetalleResponse(
        Integer idProductoVariante,
        String sku,
        Integer colorId,
        String colorNombre,
        String colorHex,
        Integer tallaId,
        String tallaNombre,
        Double precio,
        Double precioOferta,
        LocalDateTime ofertaInicio,
        LocalDateTime ofertaFin,
        Integer stock,
        String estado
) {
}
