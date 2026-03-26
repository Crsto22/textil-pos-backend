package com.sistemapos.sistematextil.util.venta;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VentaAnulacionRequest(
        @NotBlank(message = "El codigoMotivo es obligatorio")
        @Pattern(regexp = "01", message = "El codigoMotivo para anular una venta debe ser 01")
        String codigoMotivo,

        @NotBlank(message = "La descripcionMotivo es obligatoria")
        @Size(min = 5, max = 255, message = "La descripcionMotivo debe tener entre 5 y 255 caracteres")
        String descripcionMotivo) {
}
