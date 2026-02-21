package com.sistemapos.sistematextil.util.usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsuarioResetPasswordRequest(

        @NotBlank(message = "Ingrese nueva contrasena")
        @Size(min = 8, message = "La nueva contrasena debe tener al menos 8 caracteres")
        String passwordNueva,

        @NotBlank(message = "Confirme la nueva contrasena")
        String confirmarPassword
) {
}

