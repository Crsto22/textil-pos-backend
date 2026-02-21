package com.sistemapos.sistematextil.util.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AuthenticationRequest(

    @NotBlank(message = "Ingrese correo")
    @Email(message = "Formato de correo inválido")
    String email,

    @NotBlank(message = "Ingrese contraseña")
    String password

) {
}
