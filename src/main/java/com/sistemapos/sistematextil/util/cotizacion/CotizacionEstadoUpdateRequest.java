package com.sistemapos.sistematextil.util.cotizacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CotizacionEstadoUpdateRequest(
        @NotBlank(message = "Ingrese estado")
        @Size(max = 20, message = "El estado no debe superar 20 caracteres")
        String estado
) {
}
