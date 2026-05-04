package com.sistemapos.sistematextil.util.producto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductoImportVarianteRequest(
        @NotBlank(message = "colorNombre es obligatorio")
        @Size(max = 50, message = "colorNombre no debe superar 50 caracteres")
        String colorNombre,

        @Size(max = 20, message = "colorHex no debe superar 20 caracteres")
        String colorHex,

        @NotBlank(message = "tallaNombre es obligatorio")
        @Size(max = 20, message = "tallaNombre no debe superar 20 caracteres")
        String tallaNombre,

        @Size(max = 100, message = "sku no debe superar 100 caracteres")
        String sku,

        @Size(max = 100, message = "codigoBarras no debe superar 100 caracteres")
        String codigoBarras,

        String precio,

        String precioMayor,

        String stock
) {
}
