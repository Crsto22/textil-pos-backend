package com.sistemapos.sistematextil.util;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(

        @NotBlank(message = "Ingrese contrasena actual")
        String passwordActual,

        @NotBlank(message = "Ingrese nueva contrasena")
        @Size(min = 8, message = "La nueva contrasena debe tener al menos 8 caracteres")
        String passwordNueva,

        @NotBlank(message = "Confirme la nueva contrasena")
        String confirmarPassword
) {
}

