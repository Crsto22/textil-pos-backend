package com.sistemapos.sistematextil.util.producto;

public record ProductoVarianteStockSucursalRow(
        Integer varianteId,
        Integer idSucursal,
        String nombreSucursal,
        Integer stock
) {
    public ProductoVarianteStockSucursalRow(
            Integer varianteId,
            Integer idSucursal,
            String nombreSucursal,
            Long stock) {
        this(
                varianteId,
                idSucursal,
                nombreSucursal,
                stock == null ? 0 : stock.intValue());
    }
}
