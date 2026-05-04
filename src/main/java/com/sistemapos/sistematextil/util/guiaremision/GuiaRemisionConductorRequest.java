package com.sistemapos.sistematextil.util.guiaremision;

import jakarta.validation.constraints.Size;

public record GuiaRemisionConductorRequest(
        @Size(max = 1) String tipoDocumento,
        @Size(max = 20) String nroDocumento,
        @Size(max = 100) String nombres,
        @Size(max = 100) String apellidos,
        @Size(max = 20) String licencia,
        Boolean esPrincipal) {
}
