package com.sistemapos.sistematextil.util.ecommerce;

import java.util.List;

public record EcommerceProductoListadoResponse(
        boolean tiendaConfigurada,
        String message,
        List<EcommerceProductoColorListItemResponse> content,
        int page,
        int size,
        int totalPages,
        long totalElements,
        int numberOfElements,
        boolean first,
        boolean last,
        boolean empty
) {
}
