package com.sistemapos.sistematextil.util.ecommerce;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record EcommerceCarritoResumenRequest(
        @Valid @NotEmpty List<Item> items
) {
    public record Item(
            @NotNull Integer idProductoVariante,
            @NotNull
            @Min(value = 1, message = "La cantidad debe ser mayor o igual a 1")
            @Max(value = 5, message = "La cantidad maxima por variante es 5")
            Integer cantidad
    ) {
    }
}
