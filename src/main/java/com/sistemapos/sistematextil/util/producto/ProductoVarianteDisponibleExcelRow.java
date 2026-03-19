package com.sistemapos.sistematextil.util.producto;

import java.time.LocalDateTime;

public record ProductoVarianteDisponibleExcelRow(
        Integer idProductoVariante,
        String sku,
        String productoNombre,
        String categoriaNombre,
        String sucursalNombre,
        String colorNombre,
        String tallaNombre,
        Integer stock,
        Double precio,
        Double precioMayor,
        Double precioOferta,
        String estado,
        LocalDateTime ofertaInicio,
        LocalDateTime ofertaFin
) {
}
