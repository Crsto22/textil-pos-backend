package com.sistemapos.sistematextil.util.ecommerce;

import java.util.List;

public record EcommerceProductoGlobalListItemResponse(
        EcommerceProductoColorListItemResponse.ProductoItem producto,
        Double precioMinimo,
        Double precioMaximo,
        String estadoStock,
        Integer stockTotal,
        List<EcommerceProductoColorListItemResponse.PromocionComboItem> promocionesCombo,
        List<ColorOpcionItem> colores
) {
    public record ColorOpcionItem(
            EcommerceProductoColorListItemResponse.ColorItem color,
            EcommerceProductoColorListItemResponse.ImagenItem imagenPrincipal,
            Double precioMinimo,
            Double precioMaximo,
            String estadoStock,
            Integer stockTotalColor,
            List<EcommerceProductoColorListItemResponse.VarianteItem> variantes
    ) {
    }
}
