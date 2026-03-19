package com.sistemapos.sistematextil.util.dashboard;

public record DashboardTopProductoItem(
        Integer idProductoVariante,
        String producto,
        String color,
        String talla,
        long cantidadVendida) {
}
