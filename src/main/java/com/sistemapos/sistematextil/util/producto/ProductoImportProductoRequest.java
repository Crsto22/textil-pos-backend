package com.sistemapos.sistematextil.util.producto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record ProductoImportProductoRequest(
        @NotBlank(message = "nombreProducto es obligatorio")
        @Size(max = 150, message = "nombreProducto no debe superar 150 caracteres")
        String nombreProducto,

        @NotBlank(message = "categoriaNombre es obligatorio")
        @Size(max = 100, message = "categoriaNombre no debe superar 100 caracteres")
        String categoriaNombre,

        @Size(max = 500, message = "descripcion no debe superar 500 caracteres")
        String descripcion,

        @NotEmpty(message = "Ingrese variantes")
        @Valid
        List<ProductoImportVarianteRequest> variantes
) {
}
