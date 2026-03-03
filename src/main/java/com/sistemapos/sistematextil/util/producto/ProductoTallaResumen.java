package com.sistemapos.sistematextil.util.producto;

public record ProductoTallaResumen(
        Integer idProductoVariante,
        Integer tallaId,
        String nombre,
        String sku,
        String codigoExterno,
        Double precio,
        Integer stock,
        String estado
) {
}
