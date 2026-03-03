package com.sistemapos.sistematextil.util.producto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductoVarianteCreateItem(
        @NotNull(message = "Ingrese colorId")
        Integer colorId,

        @NotNull(message = "Ingrese tallaId")
        Integer tallaId,

        @NotBlank(message = "El SKU es obligatorio")
        @Size(max = 100, message = "El SKU no debe superar 100 caracteres")
        String sku,

        @Size(max = 100, message = "El codigo externo no debe superar 100 caracteres")
        String codigoExterno,

        @NotNull(message = "Ingrese precio")
        @DecimalMin(value = "0.0", inclusive = true, message = "El precio no puede ser negativo")
        Double precio,

        @NotNull(message = "Ingrese stock")
        @Min(value = 0, message = "El stock no puede ser negativo")
        Integer stock
) {
}
