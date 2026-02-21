package com.sistemapos.sistematextil.util.talla;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TallaCreateRequest(
        @NotBlank(message = "El nombre de la talla es obligatorio")
        @Size(max = 20, message = "El nombre de la talla no debe superar 20 caracteres")
        String nombre
) {
}
