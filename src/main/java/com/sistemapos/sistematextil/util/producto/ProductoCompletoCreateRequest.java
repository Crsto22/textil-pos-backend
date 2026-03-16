package com.sistemapos.sistematextil.util.producto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductoCompletoCreateRequest(
        Integer idSucursal,

        @NotNull(message = "Ingrese idCategoria")
        Integer idCategoria,

        @NotBlank(message = "El nombre del producto es obligatorio")
        @Size(max = 150, message = "El nombre del producto no debe superar 150 caracteres")
        String nombre,

        @Size(max = 500, message = "La descripcion no debe superar 500 caracteres")
        String descripcion,

        @NotEmpty(message = "Ingrese variantes del producto")
        @Valid
        List<ProductoVarianteCreateItem> variantes,

        @Valid
        List<ProductoImagenCreateItem> imagenes
) {
}
