package com.sistemapos.sistematextil.util.ecommerce;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record EcommercePromocionComboRequest(
        @NotBlank(message = "El nombre es obligatorio")
        String nombre,

        @NotNull(message = "El precio combo es obligatorio")
        @DecimalMin(value = "0.01", message = "El precio combo debe ser mayor a 0")
        BigDecimal precioCombo,

        String estado,

        LocalDateTime fechaInicio,

        LocalDateTime fechaFin,

        @NotEmpty(message = "Ingrese productos para el combo")
        List<@Valid Item> items
) {
    public record Item(
            @NotNull(message = "El producto es obligatorio")
            Integer idProducto,

            @NotNull(message = "La cantidad es obligatoria")
            Integer cantidadRequerida
    ) {
    }
}
