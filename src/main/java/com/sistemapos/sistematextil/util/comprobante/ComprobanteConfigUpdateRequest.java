package com.sistemapos.sistematextil.util.comprobante;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ComprobanteConfigUpdateRequest(
        @NotBlank(message = "Ingrese serie")
        @Size(max = 10, message = "La serie no debe superar 10 caracteres")
        String serie,

        @NotNull(message = "Ingrese ultimoCorrelativo")
        @Min(value = 0, message = "ultimoCorrelativo no puede ser negativo")
        Integer ultimoCorrelativo,

        @NotBlank(message = "Ingrese activo")
        @Pattern(regexp = "ACTIVO|INACTIVO", message = "activo permitido: ACTIVO o INACTIVO")
        String activo
) {
}
