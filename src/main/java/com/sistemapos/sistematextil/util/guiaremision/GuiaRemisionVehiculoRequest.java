package com.sistemapos.sistematextil.util.guiaremision;

import jakarta.validation.constraints.Size;

public record GuiaRemisionVehiculoRequest(
        @Size(max = 10) String placa,
        Boolean esPrincipal) {
}
