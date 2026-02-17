package com.sistemapos.sistematextil.util;

import java.time.LocalDateTime;

public record CategoriaListItemResponse(
        Integer idCategoria,
        String nombreCategoria,
        String descripcion,
        String estado,
        LocalDateTime fechaRegistro,
        Integer idSucursal,
        String nombreSucursal
) {
}
