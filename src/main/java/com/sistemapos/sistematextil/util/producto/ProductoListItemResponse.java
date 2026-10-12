package com.sistemapos.sistematextil.util.producto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProductoListItemResponse(
        Integer idProducto,
        String sku,
        String nombre,
        String slug,
        String descripcion,
        String imagenGlobalUrl,
        String imagenGlobalThumbUrl,
        String guiaTallasUrl,
        String guiaTallasThumbUrl,
        String sizeGuideAnalysisStatus,
        LocalDateTime sizeGuideAnalysisAt,
        String sizeGuideMeasurementType,
        Boolean publicarEcommerce,
        Boolean preventa,
        LocalDate fechaEnvioPreventa,
        String estado,
        LocalDateTime fechaCreacion,
        Integer idCategoria,
        String nombreCategoria,
        Integer idSucursal,
        String nombreSucursal
) {
}
