package com.sistemapos.sistematextil.util.producto;

import java.time.LocalDateTime;

public record ProductoTallaResumen(
        Integer idProductoVariante,
        Integer tallaId,
        String nombre,
        String sku,
        String codigoBarras,
        Double precio,
        Double precioMayor,
        Double precioOferta,
        LocalDateTime ofertaInicio,
        LocalDateTime ofertaFin,
        Integer stock,
        String estado
) {
}
