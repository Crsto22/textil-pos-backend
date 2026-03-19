package com.sistemapos.sistematextil.util.dashboard;

public record DashboardStockCeroItem(
        Integer idProductoVariante,
        String producto,
        String color,
        String talla,
        Integer stock,
        String sku) {
}
