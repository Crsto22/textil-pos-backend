package com.sistemapos.sistematextil.util.notacredito;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record NotaCreditoItemRequest(
        @NotNull(message = "El idVentaDetalle es obligatorio")
        Integer idVentaDetalle,

        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad debe ser mayor a 0")
        Integer cantidad) {
}
