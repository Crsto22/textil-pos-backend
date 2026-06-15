package com.sistemapos.sistematextil.util.metodopago;

import jakarta.validation.constraints.NotNull;

public record SucursalMetodoPagoConfigItemRequest(
        @NotNull(message = "Ingrese idMetodoPago")
        Integer idMetodoPago,

        Boolean activo,

        Boolean requiereCodigoOperacion,

        Boolean requiereFechaPago,

        Boolean requiereHoraPago
) {
}
