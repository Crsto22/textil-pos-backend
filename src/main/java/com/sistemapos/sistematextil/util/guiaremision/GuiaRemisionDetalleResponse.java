package com.sistemapos.sistematextil.util.guiaremision;

import java.math.BigDecimal;

public record GuiaRemisionDetalleResponse(
        Integer idGuiaRemisionDetalle,
        Integer idProductoVariante,
        String sku,
        String nombreProducto,
        String descripcion,
        BigDecimal cantidad,
        String unidadMedida,
        String codigoProducto,
        BigDecimal pesoUnitario) {
}
