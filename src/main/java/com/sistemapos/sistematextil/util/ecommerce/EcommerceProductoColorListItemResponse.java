package com.sistemapos.sistematextil.util.ecommerce;

import java.time.LocalDateTime;
import java.util.List;

import com.sistemapos.sistematextil.util.producto.TipoOfertaAplicada;

public record EcommerceProductoColorListItemResponse(
        ProductoItem producto,
        ColorItem color,
        ImagenItem imagenPrincipal,
        Double precioMinimo,
        Double precioMaximo,
        String estadoStock,
        Integer stockTotalColor,
        List<VarianteItem> variantes
) {
    public record ProductoItem(
            Integer idProducto,
            String nombre,
            String slug,
            String descripcion,
            String estado,
            LocalDateTime fechaCreacion,
            CategoriaItem categoria,
            String imagenGlobalUrl,
            String imagenGlobalThumbUrl,
            String guiaTallasUrl,
            String guiaTallasThumbUrl
    ) {
    }

    public record CategoriaItem(
            Integer idCategoria,
            String nombre
    ) {
    }

    public record ColorItem(
            Integer idColor,
            String nombre,
            String hex
    ) {
    }

    public record TallaItem(
            Integer idTalla,
            String nombre
    ) {
    }

    public record ImagenItem(
            Integer idColorImagen,
            String url,
            String urlThumb,
            Integer orden,
            Boolean esPrincipal,
            String estado,
            String origen
    ) {
    }

    public record VarianteItem(
            Integer idProductoVariante,
            String sku,
            String codigoBarras,
            TallaItem talla,
            Double precioRegular,
            Double precioMayor,
            Double precioOfertaAplicada,
            Double precioVigente,
            TipoOfertaAplicada tipoOfertaAplicada,
            Integer sucursalOfertaId,
            LocalDateTime ofertaInicio,
            LocalDateTime ofertaFin,
            Integer stock,
            Boolean disponible,
            String estado
    ) {
    }
}
