package com.sistemapos.sistematextil.util.comprobante;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ComprobanteConfigCreateRequest(
        @NotBlank(message = "Ingrese tipoComprobante")
        @Size(max = 20, message = "tipoComprobante no debe superar 20 caracteres")
        String tipoComprobante,

        @NotBlank(message = "Ingrese serie")
        @Size(max = 10, message = "La serie no debe superar 10 caracteres")
        String serie,

        @Min(value = 0, message = "ultimoCorrelativo no puede ser negativo")
        Integer ultimoCorrelativo,

        @Pattern(
                regexp = "ACTIVO|INACTIVO",
                flags = Pattern.Flag.CASE_INSENSITIVE,
                message = "activo permitido: ACTIVO o INACTIVO")
        String activo
) {
}
