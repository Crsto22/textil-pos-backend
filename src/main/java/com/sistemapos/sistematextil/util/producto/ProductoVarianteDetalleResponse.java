package com.sistemapos.sistematextil.util.producto;

public record ProductoVarianteDetalleResponse(
        Integer idProductoVariante,
        String sku,
        String codigoExterno,
        Integer colorId,
        String colorNombre,
        String colorHex,
        Integer tallaId,
        String tallaNombre,
        Double precio,
        Integer stock,
        String estado
) {
}
