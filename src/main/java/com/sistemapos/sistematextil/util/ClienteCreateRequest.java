package com.sistemapos.sistematextil.util;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClienteCreateRequest(
        @NotNull(message = "Ingrese idSucursal")
        Integer idSucursal,

        @NotNull(message = "Ingrese tipo de documento")
        TipoDocumento tipoDocumento,

        String nroDocumento,

        @NotBlank(message = "Ingrese nombres")
        @Size(min = 2, max = 150, message = "Los nombres deben tener entre 2 y 150 caracteres")
        String nombres,

        @Pattern(regexp = "^$|\\d{7,20}$", message = "El telefono debe tener entre 7 y 20 digitos")
        String telefono,

        @Email(message = "Formato de correo invalido")
        String correo,

        @Size(max = 255, message = "La direccion no debe superar 255 caracteres")
        String direccion
) {
}
