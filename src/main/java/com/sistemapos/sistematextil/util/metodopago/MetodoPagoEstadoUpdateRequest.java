package com.sistemapos.sistematextil.util.metodopago;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MetodoPagoEstadoUpdateRequest(

        @NotBlank(message = "Ingrese estado")
        @Pattern(
                regexp = "ACTIVO|INACTIVO",
                flags = Pattern.Flag.CASE_INSENSITIVE,
                message = "Estado permitido: ACTIVO o INACTIVO")
        String estado
) {
}
