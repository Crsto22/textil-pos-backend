package com.sistemapos.sistematextil.util.traslado;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TrasladoItemCreateRequest(
        @NotNull(message = "Ingrese idProductoVariante")
        Integer idProductoVariante,

        @NotNull(message = "Ingrese cantidad")
        @Min(value = 1, message = "La cantidad debe ser mayor a 0")
        Integer cantidad) {
}