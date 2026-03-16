package com.sistemapos.sistematextil.util.venta;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VentaPagoCreateItem(
        @NotNull(message = "Ingrese idMetodoPago")
        Integer idMetodoPago,

        @NotNull(message = "Ingrese monto")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
        Double monto,

        @Size(max = 100, message = "El codigo de operacion no debe superar 100 caracteres")
        String codigoOperacion
) {
}
