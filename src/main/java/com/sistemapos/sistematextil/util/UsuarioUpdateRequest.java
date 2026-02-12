package com.sistemapos.sistematextil.util;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UsuarioUpdateRequest(

        @NotBlank(message = "Ingrese un nombre")
        @Size(min = 2, max = 50, message = "El nombre debe tener entre 2 y 50 caracteres")
        String nombre,

        @NotBlank(message = "Ingrese un apellido")
        @Size(min = 2, max = 50, message = "El apellido debe tener entre 2 y 50 caracteres")
        String apellido,

        @NotBlank(message = "Ingrese DNI")
        @Pattern(regexp = "\\d{8}", message = "El DNI debe tener exactamente 8 digitos")
        String dni,

        @NotBlank(message = "Ingrese telefono")
        @Pattern(regexp = "\\d{9}", message = "El telefono debe tener exactamente 9 digitos")
        String telefono,

        @NotBlank(message = "Ingrese correo")
        @Email(message = "Formato de correo invalido")
        String correo,

        @NotNull(message = "Ingrese rol")
        Rol rol,

        @NotBlank(message = "Ingrese estado")
        @Pattern(regexp = "ACTIVO|INACTIVO", message = "Estado permitido: ACTIVO o INACTIVO")
        String estado,

        @NotNull(message = "La sucursal es obligatoria")
        Integer idSucursal
) {
}
