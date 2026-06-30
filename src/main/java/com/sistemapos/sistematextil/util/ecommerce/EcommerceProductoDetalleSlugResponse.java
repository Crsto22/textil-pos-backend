package com.sistemapos.sistematextil.util.ecommerce;

import java.util.List;

public record EcommerceProductoDetalleSlugResponse(
        boolean tiendaConfigurada,
        EcommerceProductoColorListItemResponse.ProductoItem producto,
        List<ColorCompletoItem> colores,
        List<EcommerceProductoColorListItemResponse> recomendados
) {
    public record ColorCompletoItem(
            EcommerceProductoColorListItemResponse.ColorItem color,
            EcommerceProductoColorListItemResponse.ImagenItem imagenPrincipal,
            List<EcommerceProductoColorListItemResponse.ImagenItem> imagenes,
            Double precioMinimo,
            Double precioMaximo,
            String estadoStock,
            Integer stockTotalColor,
            List<EcommerceProductoColorListItemResponse.VarianteItem> variantes
    ) {
    }
}
