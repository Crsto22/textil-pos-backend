package com.sistemapos.sistematextil.util.guiaremision;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GuiaRemisionDocumentoRelacionadoRequest(
        @NotBlank(message = "tipoDocumento es obligatorio")
        @Pattern(regexp = "01|03|04", message = "tipoDocumento permitido: 01 factura, 03 boleta o 04 liquidacion de compra")
        String tipoDocumento,

        @NotBlank(message = "serie es obligatoria")
        @Size(max = 4, message = "serie no debe superar 4 caracteres")
        String serie,

        @NotBlank(message = "numero es obligatorio")
        @Size(max = 20, message = "numero no debe superar 20 caracteres")
        String numero) {
}
