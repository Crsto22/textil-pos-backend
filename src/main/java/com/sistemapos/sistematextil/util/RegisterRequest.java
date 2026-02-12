package com.sistemapos.sistematextil.util;

import jakarta.persistence.Column;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

    @NotBlank(message = "Ingrese nombre")
    String nombre,

    @NotBlank(message = "Ingrese apellido")
    String apellido,

    @NotBlank(message = "Ingrese DNI")
    String dni,

    @NotBlank(message = "Ingrese teléfono")
    @Pattern(regexp = "\\d{9}",message = "El teléfono debe tener exactamente 9 dígitos")
    @Column(nullable = false, unique = true, length = 9)
    String telefono,

    @NotBlank(message = "Ingrese correo")
    @Email(message = "Correo inválido")
    String email,

    @NotBlank(message = "Ingrese contraseña")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    String password,

    @NotNull(message = "Ingrese rol")
    Rol rol,

    @NotNull(message = "La sucursal es obligatoria")
    Integer idSucursal

) {

}
