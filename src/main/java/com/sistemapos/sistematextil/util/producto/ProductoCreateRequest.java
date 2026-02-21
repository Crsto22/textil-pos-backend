package com.sistemapos.sistematextil.util.producto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductoCreateRequest(
        Integer idSucursal,

        @NotNull(message = "Ingrese idCategoria")
        Integer idCategoria,

        @NotBlank(message = "El SKU es obligatorio")
        @Size(max = 100, message = "El SKU no debe superar 100 caracteres")
        String sku,

        @NotBlank(message = "El nombre del producto es obligatorio")
        @Size(max = 150, message = "El nombre del producto no debe superar 150 caracteres")
        String nombre,

        @Size(max = 500, message = "La descripcion no debe superar 500 caracteres")
        String descripcion,

        @Size(max = 100, message = "El codigo externo no debe superar 100 caracteres")
        String codigoExterno
) {
}
