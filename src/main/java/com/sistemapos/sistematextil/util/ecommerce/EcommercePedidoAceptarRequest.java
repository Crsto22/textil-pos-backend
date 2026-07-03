package com.sistemapos.sistematextil.util.ecommerce;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EcommercePedidoAceptarRequest(
        @NotBlank(message = "El tipoComprobante es obligatorio")
        @Size(max = 20, message = "El tipoComprobante no debe superar 20 caracteres")
        String tipoComprobante,

        @NotBlank(message = "La serie es obligatoria")
        @Size(max = 10, message = "La serie no debe superar 10 caracteres")
        String serie,

        @Size(max = 11, message = "El RUC no debe superar 11 caracteres")
        String facturaRuc,

        @Size(max = 8, message = "El DNI no debe superar 8 caracteres")
        String dniCliente,

        Boolean consumidorFinal,

        @Size(max = 150, message = "La razon social no debe superar 150 caracteres")
        String razonSocial
) {
}
