package com.sistemapos.sistematextil.util.metodopago;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MetodoPagoCuentaRequest(
        @NotBlank(message = "Ingrese numeroCuenta")
        @Size(max = 50, message = "numeroCuenta no debe superar 50 caracteres")
        String numeroCuenta,
        @Size(max = 150, message = "titular no debe superar 150 caracteres") String titular,
        @Size(max = 500, message = "aliasesValidacion no debe superar 500 caracteres") String aliasesValidacion
) {
}
