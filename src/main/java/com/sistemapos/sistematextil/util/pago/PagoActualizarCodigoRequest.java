package com.sistemapos.sistematextil.util.pago;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Size;

public record PagoActualizarCodigoRequest(
        @Size(max = 100, message = "El codigo de operacion no debe superar 100 caracteres")
        String codigoOperacion,

        LocalDateTime fecha) {
}
