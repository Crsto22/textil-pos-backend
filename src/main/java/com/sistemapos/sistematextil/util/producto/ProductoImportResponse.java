package com.sistemapos.sistematextil.util.producto;

public record ProductoImportResponse(
        int filasProcesadas,
        int productosCreados,
        int productosActualizados,
        int variantesGuardadas,
        int coloresCreados,
        int tallasCreadas
) {
}
