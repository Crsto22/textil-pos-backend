package com.sistemapos.sistematextil.util.ecommerce;

import java.util.List;

public record EcommerceProductoColorStockResponse(
        Integer idProducto,
        String slug,
        EcommerceProductoColorListItemResponse.ColorItem color,
        String estadoStock,
        Integer stockTotalColor,
        List<EcommerceProductoColorListItemResponse.VarianteItem> variantes
) {
}
