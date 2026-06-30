package com.sistemapos.sistematextil.util.ecommerce;

import java.time.LocalDateTime;

public interface EcommerceProductoColorGroupProjection {
    Integer getProductoId();

    String getProductoNombre();

    String getProductoSlug();

    String getProductoDescripcion();

    String getProductoEstado();

    LocalDateTime getFechaCreacion();

    String getImagenGlobalUrl();

    String getImagenGlobalThumbUrl();

    Integer getCategoriaId();

    String getCategoriaNombre();

    Integer getColorId();

    String getColorNombre();

    String getColorHex();

    Long getStockTotalColor();

    Long getTotalVariantes();

    Long getVariantesConStock();
}
