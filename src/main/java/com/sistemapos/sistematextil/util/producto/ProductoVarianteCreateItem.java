package com.sistemapos.sistematextil.util.producto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ProductoVarianteCreateItem(
        @NotNull(message = "Ingrese colorId")
        Integer colorId,

        @NotNull(message = "Ingrese tallaId")
        Integer tallaId,

        @NotNull(message = "Ingrese precio")
        @DecimalMin(value = "0.0", inclusive = true, message = "El precio no puede ser negativo")
        Double precio,

        @NotNull(message = "Ingrese stock")
        @Min(value = 0, message = "El stock no puede ser negativo")
        Integer stock
) {
}
