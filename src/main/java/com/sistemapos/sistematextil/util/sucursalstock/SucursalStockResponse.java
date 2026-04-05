package com.sistemapos.sistematextil.util.sucursalstock;

public record SucursalStockResponse(
        Integer idSucursalStock,
        Integer idSucursal,
        String nombreSucursal,
        String tipoSucursal,
        Integer idProductoVariante,
        Integer idProducto,
        String producto,
        String sku,
        String codigoBarras,
        String color,
        String talla,
        Integer cantidad,
        Double precio,
        String estadoVariante) {
}
