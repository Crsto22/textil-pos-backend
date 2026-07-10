package com.sistemapos.sistematextil.util.ecommerce;

import java.util.List;

public record EcommerceInicioResponse(
        boolean tiendaConfigurada,
        List<EcommercePortadaResponse> portadas,
        List<EcommerceInicioImagenProductoResponse> imagenesProductos,
        List<EcommerceProductoColorListItemResponse> aleatorios,
        List<EcommerceProductoColorListItemResponse> masVendidos,
        List<EcommerceInicioComboResponse> combos
) {
}
