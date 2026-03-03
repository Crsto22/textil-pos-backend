package com.sistemapos.sistematextil.util.producto;

public record ProductoVarianteResumenRow(
        Integer productoId,
        Integer varianteId,
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
