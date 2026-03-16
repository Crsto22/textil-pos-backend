package com.sistemapos.sistematextil.util.pago;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PagoActualizarCodigoRequest(
        @NotBlank(message = "El código de operación no puede estar vacío")
        @Size(max = 100, message = "El código de operación no puede exceder los 100 caracteres")
        String codigoOperacion) {
}
