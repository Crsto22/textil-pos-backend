package com.sistemapos.sistematextil.util.producto;

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
        Integer stock,
        String estado
) {
}
