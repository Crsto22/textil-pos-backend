package com.sistemapos.sistematextil.util.producto;

import java.time.LocalDateTime;

public record ProductoVarianteOfertaListItemResponse(
        Integer idProductoVariante,
        Integer productoId,
        String productoNombre,
        Integer sucursalId,
        String sucursalNombre,
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
        String imagenUrl,
        Integer stock,
        String estado
) {
}
