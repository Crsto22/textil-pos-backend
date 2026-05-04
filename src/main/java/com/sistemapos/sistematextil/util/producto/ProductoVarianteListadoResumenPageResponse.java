package com.sistemapos.sistematextil.util.producto;

import java.util.List;

import org.springframework.data.domain.Page;

public record ProductoVarianteListadoResumenPageResponse(
        List<ProductoVarianteListadoResumenResponse> content,
        List<ImagenGrupoItem> imagenesPorColor,
        int page,
        int size,
        int totalPages,
        long totalElements,
        int numberOfElements,
        boolean first,
        boolean last,
        boolean empty
) {
    public static ProductoVarianteListadoResumenPageResponse fromPage(
            Page<ProductoVarianteListadoResumenResponse> page,
            List<ImagenGrupoItem> imagenesPorColor) {
        return new ProductoVarianteListadoResumenPageResponse(
                page.getContent(),
                imagenesPorColor,
                page.getNumber(),
                page.getSize(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.getNumberOfElements(),
                page.isFirst(),
                page.isLast(),
                page.isEmpty());
    }

    public record ImagenGrupoItem(
            String key,
            Integer idProducto,
            Integer idColor,
            ProductoVarianteListadoResumenResponse.ImagenItem imagenPrincipal,
            List<ProductoVarianteListadoResumenResponse.ImagenItem> imagenes
    ) {
    }
}
