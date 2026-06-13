package com.sistemapos.sistematextil.util.ecommerce;

import java.util.List;

public record EcommerceInicioResponse(
        boolean tiendaConfigurada,
        List<EcommerceProductoColorListItemResponse> aleatorios,
        List<EcommerceProductoColorListItemResponse> masVendidos
) {
}
