package com.sistemapos.sistematextil.util.producto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record ProductoVarianteOfertaLoteUpdateRequest(
        @NotEmpty(message = "Ingrese al menos una variante para actualizar ofertas")
        @Valid
        List<ProductoVarianteOfertaLoteItemRequest> items
) {
}
