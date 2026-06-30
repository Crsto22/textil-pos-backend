package com.sistemapos.sistematextil.util.ecommerce;

import java.util.List;

import com.sistemapos.sistematextil.util.paginacion.PagedResponse;

public record EcommercePedidoAdminPageResponse(
        List<EcommercePedidoAdminResponse> content,
        int page,
        int size,
        int totalPages,
        long totalElements,
        int numberOfElements,
        boolean first,
        boolean last,
        boolean empty,
        EcommercePedidoEstadisticasResponse estadisticas
) {
    public static EcommercePedidoAdminPageResponse from(
            PagedResponse<EcommercePedidoAdminResponse> page,
            EcommercePedidoEstadisticasResponse estadisticas) {
        return new EcommercePedidoAdminPageResponse(
                page.content(),
                page.page(),
                page.size(),
                page.totalPages(),
                page.totalElements(),
                page.numberOfElements(),
                page.first(),
                page.last(),
                page.empty(),
                estadisticas);
    }
}
