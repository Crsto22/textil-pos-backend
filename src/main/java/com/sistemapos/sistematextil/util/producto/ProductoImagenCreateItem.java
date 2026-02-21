package com.sistemapos.sistematextil.util.producto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProductoImagenCreateItem(
        @NotNull(message = "Ingrese colorId")
        Integer colorId,

        @NotBlank(message = "La url de la imagen es obligatoria")
        String url,

        @NotBlank(message = "La url del thumbnail es obligatoria")
        String urlThumb,

        @NotNull(message = "Ingrese orden")
        @Min(value = 1, message = "El orden debe ser mayor o igual a 1")
        Integer orden,

        Boolean esPrincipal
) {
}
