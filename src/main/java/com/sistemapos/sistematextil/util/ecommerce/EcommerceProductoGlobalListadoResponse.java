package com.sistemapos.sistematextil.util.ecommerce;

import java.util.List;

public record EcommerceProductoGlobalListadoResponse(
        boolean tiendaConfigurada,
        String message,
        List<EcommerceProductoGlobalListItemResponse> content,
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
