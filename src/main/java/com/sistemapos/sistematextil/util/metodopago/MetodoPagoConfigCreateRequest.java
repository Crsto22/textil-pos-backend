package com.sistemapos.sistematextil.util.metodopago;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MetodoPagoConfigCreateRequest(
        @NotBlank(message = "Ingrese nombre")
        @Size(max = 50, message = "nombre no debe superar 50 caracteres")
        String nombre,

        @Size(max = 255, message = "descripcion no debe superar 255 caracteres")
        String descripcion,

        @Pattern(
                regexp = "ACTIVO|INACTIVO",
                flags = Pattern.Flag.CASE_INSENSITIVE,
                message = "estado permitido: ACTIVO o INACTIVO")
        String estado,

        List<@Valid MetodoPagoCuentaRequest> cuentas
) {
}
