package com.sistemapos.sistematextil.util.sucursal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SucursalUpdateRequest(

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

        @Pattern(regexp = "^$|\\d{6}", message = "El ubigeo debe tener 6 digitos")
        String ubigeo,

        @Size(max = 100, message = "El departamento no puede superar 100 caracteres")
        String departamento,

        @Size(max = 100, message = "La provincia no puede superar 100 caracteres")
        String provincia,

        @Size(max = 100, message = "El distrito no puede superar 100 caracteres")
        String distrito,

        @Pattern(regexp = "^$|\\d{4}", message = "El codigoEstablecimientoSunat debe tener 4 digitos")
        String codigoEstablecimientoSunat,

        @NotBlank(message = "Ingrese estado")
        @Pattern(regexp = "ACTIVO|INACTIVO", message = "Estado permitido: ACTIVO o INACTIVO")
        String estado,

        @NotNull(message = "La empresa es obligatoria")
        Integer idEmpresa
) {
}
