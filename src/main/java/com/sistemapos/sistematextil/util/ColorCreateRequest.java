package com.sistemapos.sistematextil.util;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ColorCreateRequest(
        @NotBlank(message = "El nombre del color es obligatorio")
        @Size(max = 50, message = "El nombre del color no debe superar 50 caracteres")
        String nombre,

        @NotBlank(message = "El codigo del color es obligatorio")
        @Size(max = 20, message = "El codigo del color no debe superar 20 caracteres")
        String codigo
) {
}
