package com.sistemapos.sistematextil.util.producto;

public record ProductoVarianteResumenRow(
        Integer productoId,
        Integer colorId,
        Integer tallaId,
        String tallaNombre,
        Double precio
) {
}
