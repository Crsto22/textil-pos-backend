package com.sistemapos.sistematextil.util.documento;

import java.util.List;

public record ConsultaRucResponse(
        String ruc,
        String razonSocial,
        String nombreComercial,
        List<String> telefonos,
        String tipo,
        String estado,
        String condicion,
        String direccion,
        String departamento,
        String provincia,
        String distrito,
        String ubigeo,
        String capital) {
}
