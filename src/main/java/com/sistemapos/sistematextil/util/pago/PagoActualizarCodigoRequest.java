package com.sistemapos.sistematextil.util.pago;

import jakarta.validation.constraints.NotBlank;

public record PagoActualizarCodigoRequest(
        @NotBlank(message = "El código de operación es obligatorio")
        String codigoOperacion) {
}
