package com.sistemapos.sistematextil.util.guiaremision;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GuiaRemisionTransportistaRequest(
        @Pattern(regexp = "6", message = "transportistaTipoDoc debe ser 6")
        String transportistaTipoDoc,
        @Size(max = 20) String transportistaNroDoc,
        @Size(max = 255) String transportistaRazonSocial,
        @Size(max = 20) String transportistaRegistroMtc) {
}
