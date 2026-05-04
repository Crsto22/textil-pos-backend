package com.sistemapos.sistematextil.util.producto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record ProductoImportRequest(
        @NotNull(message = "Ingrese configuracionImportacion")
        @Valid
        ProductoImportConfigRequest configuracionImportacion,

        @NotEmpty(message = "Ingrese productos")
        @Valid
        List<ProductoImportProductoRequest> productos,

        @Valid
        ProductoImportValoresNuevosRequest valoresNuevosDetectados
) {
}
