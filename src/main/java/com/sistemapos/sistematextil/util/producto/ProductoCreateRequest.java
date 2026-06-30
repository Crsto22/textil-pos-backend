package com.sistemapos.sistematextil.util.producto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductoCreateRequest(
        @NotNull(message = "Ingrese idCategoria")
        Integer idCategoria,

        @NotBlank(message = "El nombre del producto es obligatorio")
        @Size(max = 150, message = "El nombre del producto no debe superar 150 caracteres")
        String nombre,

        @Size(max = 180, message = "El slug no debe superar 180 caracteres")
        String slug,

        @Size(max = 500, message = "La descripcion no debe superar 500 caracteres")
        String descripcion,

        @Size(max = 600, message = "La url de la imagen global no debe superar 600 caracteres")
        String imagenGlobalUrl,

        @Size(max = 600, message = "La url del thumbnail global no debe superar 600 caracteres")
        String imagenGlobalThumbUrl,

        @Size(max = 600, message = "La url de la guia de tallas no debe superar 600 caracteres")
        String guiaTallasUrl,

        @Size(max = 600, message = "La url del thumbnail de la guia de tallas no debe superar 600 caracteres")
        String guiaTallasThumbUrl,

        Boolean publicarEcommerce
) {
}
