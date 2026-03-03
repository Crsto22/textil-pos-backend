package com.sistemapos.sistematextil.util.producto;

import java.time.LocalDateTime;

public record ProductoImportHistorialListItemResponse(
        Integer idImportacion,
        Integer idUsuario,
        String nombreUsuario,
        Integer idSucursal,
        String nombreSucursal,
        String nombreArchivo,
        Long tamanoBytes,
        Integer filasProcesadas,
        Integer productosCreados,
        Integer productosActualizados,
        Integer variantesGuardadas,
        Integer categoriasCreadas,
        Integer coloresCreados,
        Integer tallasCreadas,
        String estado,
        String mensajeError,
        Integer duracionMs,
        LocalDateTime createdAt
) {
}
