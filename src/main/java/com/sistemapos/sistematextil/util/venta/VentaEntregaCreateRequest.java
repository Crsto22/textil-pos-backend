package com.sistemapos.sistematextil.util.venta;

import jakarta.validation.constraints.Size;

public record VentaEntregaCreateRequest(
        @Size(max = 20) String metodo,
        @Size(max = 180) String destinatario,
        @Size(max = 20) String documento,
        @Size(max = 30) String celular,
        Long idAgenciaShalom,
        @Size(max = 20) String estadoFilm
) {
}
