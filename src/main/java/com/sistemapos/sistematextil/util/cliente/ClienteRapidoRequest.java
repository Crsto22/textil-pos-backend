package com.sistemapos.sistematextil.util.cliente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ClienteRapidoRequest(
        @NotBlank(message = "Ingrese telefono")
        @Pattern(regexp = "\\d{7,20}", message = "El telefono debe tener entre 7 y 20 digitos")
        String telefono
) {
}
