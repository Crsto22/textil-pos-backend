package com.sistemapos.sistematextil.util.producto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductoImportConfigRequest(
        Integer idSucursalDestino,

        @Size(max = 100, message = "nombreSucursalDestino no debe superar 100 caracteres")
        String nombreSucursalDestino
) {
}
