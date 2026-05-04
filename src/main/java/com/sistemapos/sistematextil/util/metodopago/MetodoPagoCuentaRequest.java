package com.sistemapos.sistematextil.util.metodopago;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MetodoPagoCuentaRequest(
        @NotBlank(message = "Ingrese numeroCuenta")
        @Size(max = 50, message = "numeroCuenta no debe superar 50 caracteres")
        String numeroCuenta
) {
}
