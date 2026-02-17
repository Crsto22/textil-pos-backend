package com.sistemapos.sistematextil.util;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoriaUpdateRequest(
        Integer idSucursal,

        @NotBlank(message = "El nombre de la categoria es obligatorio")
        @Size(min = 3, max = 100, message = "El nombre de la categoria debe tener entre 3 y 100 caracteres")
        String nombreCategoria,

        @Size(max = 255, message = "La descripcion no debe superar 255 caracteres")
        String descripcion
) {
}
