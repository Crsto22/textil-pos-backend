package com.sistemapos.sistematextil.util.producto;

import java.time.LocalDateTime;

public record ProductoVarianteResumenRow(
        Integer productoId,
        Integer varianteId,
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
        Long stock,
        String estado
) {
}
