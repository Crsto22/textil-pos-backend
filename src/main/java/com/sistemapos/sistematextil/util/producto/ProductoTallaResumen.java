package com.sistemapos.sistematextil.util.producto;

public record ProductoTallaResumen(
        Integer idProductoVariante,
        Integer tallaId,
        String nombre,
        String sku,
        Double precio,
        Double precioOferta,
        Integer stock,
        String estado
) {
}
