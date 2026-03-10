package com.sistemapos.sistematextil.util.producto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMin;

public record ProductoVarianteOfertaUpdateRequest(
        @DecimalMin(value = "0.0", inclusive = false, message = "El precio de oferta debe ser mayor a 0")
        Double precioOferta,

        LocalDateTime ofertaInicio,

        LocalDateTime ofertaFin
) {
}
