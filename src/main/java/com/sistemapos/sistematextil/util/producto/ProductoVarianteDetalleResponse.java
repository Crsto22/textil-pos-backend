package com.sistemapos.sistematextil.util.producto;

import java.time.LocalDateTime;

public record ProductoVarianteDetalleResponse(
        Integer idProductoVariante,
        String sku,
        String codigoBarras,
        Integer colorId,
        String colorNombre,
        String colorHex,
        Integer tallaId,
        String tallaNombre,
        Double precio,
        Double precioMayor,
        Double precioOferta,
        LocalDateTime ofertaInicio,
        LocalDateTime ofertaFin,
        Integer stock,
        java.util.List<ProductoVarianteStockDetalleResponse> stocksSucursales,
        String estado
) {
}
