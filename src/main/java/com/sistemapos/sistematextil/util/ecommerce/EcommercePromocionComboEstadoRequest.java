package com.sistemapos.sistematextil.util.ecommerce;

import jakarta.validation.constraints.NotBlank;

public record EcommercePromocionComboEstadoRequest(
        @NotBlank(message = "El estado es obligatorio")
        String estado
) {
}
