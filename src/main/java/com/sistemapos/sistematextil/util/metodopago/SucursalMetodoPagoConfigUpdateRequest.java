package com.sistemapos.sistematextil.util.metodopago;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record SucursalMetodoPagoConfigUpdateRequest(
        @NotEmpty(message = "Ingrese al menos un metodo de pago")
        List<@Valid SucursalMetodoPagoConfigItemRequest> metodosPago
) {
}
