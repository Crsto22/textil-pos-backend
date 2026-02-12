package com.sistemapos.sistematextil.util;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SucursalCreateRequest(

        @NotBlank(message = "Ingrese nombre de sucursal")
        @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres")
        String nombre,

        @Size(max = 255, message = "La descripcion no puede superar los 255 caracteres")
        String descripcion,

        @NotBlank(message = "Ingrese direccion")
        @Size(max = 255, message = "La direccion no puede superar los 255 caracteres")
        String direccion,

        @NotBlank(message = "Ingrese telefono")
        @Pattern(regexp = "\\d{7,15}", message = "El telefono debe tener entre 7 y 15 digitos")
        String telefono,

        @NotBlank(message = "Ingrese correo")
        @Email(message = "Formato de correo invalido")
        String correo,

        @NotNull(message = "La empresa es obligatoria")
        Integer idEmpresa
) {
}
